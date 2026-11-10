package com.zelsignals.app.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Last error produced by [generateSignal] (null when the last call succeeded). */
    @Volatile
    var lastError: String? = null

    // ---------------------------------------------------------------- helpers

    private suspend fun httpGet(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${body.take(200)}")
            body
        }
    }

    private fun JsonObject.str(key: String): String? {
        val e = get(key) ?: return null
        return if (e.isJsonPrimitive) e.asString else null
    }

    private fun JsonObject.num(key: String): Double? {
        val e = get(key) ?: return null
        if (!e.isJsonPrimitive) return null
        return try {
            e.asDouble
        } catch (ex: Exception) {
            e.asString.replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull()
        }
    }

    private fun JsonElement.numOrNull(): Double? =
        try {
            if (isJsonPrimitive) asDouble else null
        } catch (e: Exception) {
            null
        }

    /** Extracts the full oldest-first close series from the JSON returned by [fetchMarketData]. */
    fun extractCloses(marketData: String): List<Double> {
        return try {
            val root = JsonParser.parseString(marketData).asJsonObject
            root.getAsJsonArray("closes").map { it.asDouble }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------ Twelve Data

    /**
     * Returns a JSON string: {"symbol","interval","candles":[last 20 candles, oldest first],
     * "closes":[full close series, oldest first, used by the StrategyEngine]}.
     */
    suspend fun fetchMarketData(symbol: String, interval: String, apiKey: String): String {
        val url = "https://api.twelvedata.com/time_series".toHttpUrl().newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("interval", interval)
            .addQueryParameter("outputsize", "250")
            .addQueryParameter("apikey", apiKey)
            .build()
        val root = JsonParser.parseString(httpGet(url)).asJsonObject
        if (root.str("status") == "error") {
            throw IOException(root.str("message") ?: "Twelve Data returned an error")
        }
        val values = root.getAsJsonArray("values")
        if (values == null || values.size() == 0) throw IOException("No candle data returned for $symbol")

        // Twelve Data returns newest first.
        val oldestFirst = values.reversed()
        val last20 = JsonArray().also { arr -> oldestFirst.takeLast(20).forEach { arr.add(it) } }
        val closes = JsonArray().also { arr ->
            oldestFirst.forEach { c ->
                c.asJsonObject.str("close")?.toDoubleOrNull()?.let { arr.add(it) }
            }
        }
        val out = JsonObject().apply {
            addProperty("symbol", symbol)
            addProperty("interval", interval)
            add("candles", last20)
            add("closes", closes)
        }
        return out.toString()
    }

    // --------------------------------------------------------------- MarketAux

    suspend fun fetchNewsSentiment(symbol: String, apiKey: String): List<NewsItem> {
        val builder = "https://api.marketaux.com/v1/news/all".toHttpUrl().newBuilder()
            .addQueryParameter("filter_entities", "true")
            .addQueryParameter("language", "en")
            .addQueryParameter("limit", "10")
            .addQueryParameter("api_token", apiKey)
        val isPair = symbol.contains("/")
        if (isPair) {
            builder.addQueryParameter("search", symbol.substringBefore("/"))
        } else {
            builder.addQueryParameter("symbols", symbol)
        }
        val root = JsonParser.parseString(httpGet(builder.build())).asJsonObject
        if (root.has("error") && root.get("error").isJsonObject) {
            throw IOException(root.getAsJsonObject("error").str("message") ?: "MarketAux error")
        }
        val data = root.getAsJsonArray("data") ?: return emptyList()
        return data.mapNotNull { el ->
            val o = el.asJsonObject
            val title = o.str("title") ?: return@mapNotNull null
            val source = o.str("source") ?: "MarketAux"
            val entities = o.getAsJsonArray("entities")
            val allScores = entities?.mapNotNull { it.asJsonObject.get("sentiment_score")?.numOrNull() }.orEmpty()
            val matched = entities?.filter { it.asJsonObject.str("symbol").equals(symbol, ignoreCase = true) }
                ?.mapNotNull { it.asJsonObject.get("sentiment_score")?.numOrNull() }.orEmpty()
            val scores = if (matched.isNotEmpty()) matched else allScores
            val sentiment = if (scores.isEmpty()) {
                "Neutral"
            } else {
                val avg = scores.average()
                when {
                    avg > 0.15 -> "Positive"
                    avg < -0.15 -> "Negative"
                    else -> "Neutral"
                }
            }
            NewsItem(headline = title, sentiment = sentiment, source = source)
        }
    }

    // -------------------------------------------------------------------- FRED

    private val fredSeries = listOf(
        "CPIAUCSL" to "CPI (CPIAUCSL)",
        "DFF" to "Fed Funds Rate (DFF)",
        "UNRATE" to "Unemployment (UNRATE)",
        "DGS10" to "10Y Treasury Yield (DGS10)"
    )

    suspend fun fetchMacroData(apiKey: String): List<MacroData> = coroutineScope {
        fredSeries.map { (id, label) -> async { fetchFredSeries(id, label, apiKey) } }.awaitAll()
    }

    private suspend fun fetchFredSeries(id: String, label: String, apiKey: String): MacroData {
        return try {
            val url = "https://api.stlouisfed.org/fred/series/observations".toHttpUrl().newBuilder()
                .addQueryParameter("series_id", id)
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("file_type", "json")
                .addQueryParameter("sort_order", "desc")
                .addQueryParameter("limit", "20")
                .build()
            val root = JsonParser.parseString(httpGet(url)).asJsonObject
            val obs = root.getAsJsonArray("observations")
                ?.map { it.asJsonObject }
                ?.filter { it.str("value")?.toDoubleOrNull() != null }
                .orEmpty()
            if (obs.isEmpty()) return MacroData(label, "N/A", "no data")
            val latest = obs.first()
            val value = latest.str("value")!!.toDouble()
            val date = latest.str("date") ?: ""
            val text = if (id == "CPIAUCSL") {
                val yearAgo = obs.getOrNull(12)?.str("value")?.toDoubleOrNull()
                if (yearAgo != null && yearAgo != 0.0) {
                    String.format("%.2f%% YoY (idx %.1f)", (value / yearAgo - 1.0) * 100.0, value)
                } else {
                    String.format("idx %.1f", value)
                }
            } else {
                String.format("%.2f%%", value)
            }
            MacroData(label, text, date)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MacroData(label, "Unavailable", (e.message ?: "error").take(80))
        }
    }

    // ---------------------------------------------------------------------- AI

    suspend fun generateSignal(
        aiKey: String,
        baseUrl: String,
        model: String,
        marketData: String,
        news: List<NewsItem>,
        macro: List<MacroData>,
        strategySignal: String
    ): TradeSignal? {
        lastError = null
        return try {
            val root = JsonParser.parseString(marketData).asJsonObject
            val symbol = root.str("symbol") ?: "UNKNOWN"
            val priceData = JsonObject().apply {
                addProperty("interval", root.str("interval") ?: "")
                add("candles", root.get("candles"))
            }.toString()
            val newsText = if (news.isEmpty()) "No recent news available." else
                news.joinToString("\n") { "- [${it.sentiment}] ${it.headline} (${it.source})" }
            val macroText = if (macro.isEmpty()) "No macro data available." else
                macro.joinToString("\n") { "- ${it.indicator}: ${it.value} (as of ${it.date})" }

            val systemPrompt = "You are a professional quantitative trader. Analyze the following data for $symbol:\n" +
                "1. Price Data: $priceData\n" +
                "2. Technical Strategy Signal: $strategySignal\n" +
                "3. News Sentiment:\n$newsText\n" +
                "4. Macro Data:\n$macroText\n" +
                "Generate a trade setup. Consider the news and macro data. Return ONLY a valid JSON object with: " +
                "'bias' (Bullish/Bearish/Neutral), 'confidence' (0-100), 'entry', 'takeProfit', 'stopLoss', " +
                "'positionSizePct' (suggested % of portfolio, max 2%), 'regime' (Trending/Ranging/Volatile), " +
                "'reasoning' (mentioning news/macro impact), and 'risks' (array of strings)."

            val messages = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemPrompt)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", "Generate the trade setup JSON for $symbol now.")
                })
            }
            val payload = JsonObject().apply {
                addProperty("model", model)
                addProperty("temperature", 0.2)
                add("messages", messages)
            }.toString()

            val endpoint = baseUrl.trim().trimEnd('/') + "/chat/completions"
            val body = withContext(Dispatchers.IO) {
                val req = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $aiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw IOException("AI HTTP ${resp.code}: ${text.take(200)}")
                    text
                }
            }

            val content = JsonParser.parseString(body).asJsonObject
                .getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message").str("content")
                ?: throw IOException("AI response had no content")

            val start = content.indexOf('{')
            val end = content.lastIndexOf('}')
            if (start < 0 || end <= start) throw IOException("AI did not return JSON")
            val o = JsonParser.parseString(content.substring(start, end + 1)).asJsonObject

            val rawBias = (o.str("bias") ?: "Neutral").lowercase()
            val bias = when {
                rawBias.contains("bull") -> "Bullish"
                rawBias.contains("bear") -> "Bearish"
                else -> "Neutral"
            }
            val rawRegime = o.str("regime") ?: "Ranging"
            val regime = when {
                rawRegime.contains("trend", true) -> "Trending"
                rawRegime.contains("rang", true) -> "Ranging"
                rawRegime.contains("volat", true) -> "Volatile"
                else -> rawRegime
            }
            val risks: List<String> = o.get("risks").let { r ->
                when {
                    r == null || r.isJsonNull -> emptyList()
                    r.isJsonArray -> r.asJsonArray.map { if (it.isJsonPrimitive) it.asString else it.toString() }
                    r.isJsonPrimitive -> listOf(r.asString)
                    else -> emptyList()
                }
            }
            TradeSignal(
                bias = bias,
                confidence = (o.num("confidence") ?: 0.0).toInt().coerceIn(0, 100),
                entry = o.num("entry") ?: 0.0,
                takeProfit = o.num("takeProfit") ?: 0.0,
                stopLoss = o.num("stopLoss") ?: 0.0,
                positionSizePct = (o.num("positionSizePct") ?: 0.0).coerceIn(0.0, 2.0),
                regime = regime,
                reasoning = o.str("reasoning") ?: "",
                risks = risks
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            null
        }
    }
}
