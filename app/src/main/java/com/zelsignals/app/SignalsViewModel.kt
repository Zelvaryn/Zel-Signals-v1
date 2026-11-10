package com.zelsignals.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zelsignals.app.data.ApiService
import com.zelsignals.app.data.MacroData
import com.zelsignals.app.data.NewsItem
import com.zelsignals.app.data.PaperTrade
import com.zelsignals.app.data.StrategyEngine
import com.zelsignals.app.data.TradeSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class AppSettings(
    val twelveKey: String = "",
    val marketAuxKey: String = "",
    val fredKey: String = "",
    val aiKey: String = "",
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val model: String = "llama-3.3-70b-versatile"
)

data class IndicatorSnapshot(
    val price: Double,
    val ema20: Double,
    val ema50: Double,
    val ema200: Double,
    val rsi: Double,
    val bbUpper: Double,
    val bbLower: Double,
    val macd: Double,
    val macdSignal: Double
)

class SignalsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("zel_signals_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _symbol = MutableStateFlow(prefs.getString("symbol", "EUR/USD") ?: "EUR/USD")
    val symbol: StateFlow<String> = _symbol.asStateFlow()

    private val _timeframe = MutableStateFlow(prefs.getString("timeframe", "1h") ?: "1h")
    val timeframe: StateFlow<String> = _timeframe.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentSignal = MutableStateFlow<TradeSignal?>(null)
    val currentSignal: StateFlow<TradeSignal?> = _currentSignal.asStateFlow()

    private val _currentNews = MutableStateFlow<List<NewsItem>>(emptyList())
    val currentNews: StateFlow<List<NewsItem>> = _currentNews.asStateFlow()

    private val _currentMacro = MutableStateFlow<List<MacroData>>(emptyList())
    val currentMacro: StateFlow<List<MacroData>> = _currentMacro.asStateFlow()

    private val _history = MutableStateFlow<List<PaperTrade>>(loadHistory())
    val history: StateFlow<List<PaperTrade>> = _history.asStateFlow()

    private val _resolved = MutableStateFlow<Set<Long>>(loadResolved())
    val resolved: StateFlow<Set<Long>> = _resolved.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _strategySignal = MutableStateFlow("Neutral")
    val strategySignal: StateFlow<String> = _strategySignal.asStateFlow()

    private val _indicators = MutableStateFlow<IndicatorSnapshot?>(null)
    val indicators: StateFlow<IndicatorSnapshot?> = _indicators.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // ------------------------------------------------------------- setters

    fun setSymbol(value: String) {
        _symbol.value = value
        prefs.edit().putString("symbol", value).apply()
    }

    fun setTimeframe(value: String) {
        _timeframe.value = value
        prefs.edit().putString("timeframe", value).apply()
    }

    fun updateSettings(s: AppSettings) {
        _settings.value = s
        prefs.edit()
            .putString("twelveKey", s.twelveKey.trim())
            .putString("marketAuxKey", s.marketAuxKey.trim())
            .putString("fredKey", s.fredKey.trim())
            .putString("aiKey", s.aiKey.trim())
            .putString("baseUrl", s.baseUrl.trim())
            .putString("model", s.model.trim())
            .apply()
        log("Settings saved.")
    }

    // ------------------------------------------------------------ analysis

    fun analyzeMarket(
        twelveKey: String,
        marketAuxKey: String,
        fredKey: String,
        aiKey: String,
        baseUrl: String,
        model: String
    ) {
        if (_isLoading.value) return
        val sym = _symbol.value.trim().uppercase(Locale.US)
        val tf = _timeframe.value
        if (sym.isEmpty()) {
            log("Enter a symbol first.")
            return
        }
        if (twelveKey.isBlank()) {
            log("Twelve Data key missing - open Settings.")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                log("Fetching market, news and macro data for $sym ($tf)...")
                val (market, news, macro) = coroutineScope {
                    val m = async { ApiService.fetchMarketData(sym, tf, twelveKey) }
                    val n = async {
                        if (marketAuxKey.isBlank()) {
                            log("News skipped (no MarketAux key).")
                            emptyList<NewsItem>()
                        } else {
                            safely("News", emptyList()) { ApiService.fetchNewsSentiment(sym, marketAuxKey) }
                        }
                    }
                    val f = async {
                        if (fredKey.isBlank()) {
                            log("Macro skipped (no FRED key).")
                            emptyList<MacroData>()
                        } else {
                            safely("Macro", emptyList()) { ApiService.fetchMacroData(fredKey) }
                        }
                    }
                    Triple(m.await(), n.await(), f.await())
                }

                val closes = ApiService.extractCloses(market)
                if (closes.size < 2) throw IllegalStateException("Not enough price data returned.")

                val strategy = StrategyEngine.generateStrategySignal(closes)
                _strategySignal.value = strategy
                _indicators.value = snapshot(closes)
                log("Strategy signal: $strategy (${closes.size} candles).")

                _currentNews.value = news
                _currentMacro.value = macro

                if (aiKey.isBlank()) {
                    _currentSignal.value = null
                    log("AI key missing - skipped AI signal.")
                } else {
                    log("Asking AI ($model) for a trade setup...")
                    val signal = ApiService.generateSignal(aiKey, baseUrl, model, market, news, macro, strategy)
                    _currentSignal.value = signal
                    if (signal == null) {
                        log("AI signal failed: ${ApiService.lastError ?: "unknown error"}")
                    } else {
                        log("Signal ready: ${signal.bias} (${signal.confidence}%).")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun <T> safely(label: String, fallback: T, block: suspend () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("$label failed: ${e.message}")
            fallback
        }
    }

    private fun snapshot(closes: List<Double>): IndicatorSnapshot {
        val (upper, lower) = StrategyEngine.calculateBollingerBands(closes)
        val (macd, sig) = StrategyEngine.calculateMACD(closes)
        return IndicatorSnapshot(
            price = closes.last(),
            ema20 = StrategyEngine.calculateEMA(closes, 20).last(),
            ema50 = StrategyEngine.calculateEMA(closes, 50).last(),
            ema200 = StrategyEngine.calculateEMA(closes, 200).last(),
            rsi = StrategyEngine.calculateRSI(closes),
            bbUpper = upper,
            bbLower = lower,
            macd = macd,
            macdSignal = sig
        )
    }

    // ------------------------------------------------------------- journal

    fun savePaperTrade(trade: PaperTrade) {
        _history.value = listOf(trade) + _history.value
        persistHistory()
        log("Saved paper trade: ${trade.symbol} ${trade.bias}.")
    }

    /** Marks an existing journal trade as a win or loss (and counts it in the win rate). */
    fun markTrade(trade: PaperTrade, isWin: Boolean) {
        _resolved.value = _resolved.value + trade.timestamp
        _history.value = _history.value.map {
            if (it.timestamp == trade.timestamp) it.copy(isWin = isWin) else it
        }
        persistHistory()
    }

    fun deleteTrade(trade: PaperTrade) {
        _history.value = _history.value.filter { it.timestamp != trade.timestamp }
        _resolved.value = _resolved.value - trade.timestamp
        persistHistory()
    }

    fun isResolved(trade: PaperTrade): Boolean = trade.timestamp in _resolved.value

    fun calculateWinRate(): Int {
        val done = _history.value.filter { it.timestamp in _resolved.value }
        if (done.isEmpty()) return 0
        return (done.count { it.isWin } * 100.0 / done.size).roundToInt()
    }

    // --------------------------------------------------------- persistence

    private fun persistHistory() {
        prefs.edit()
            .putString("history", gson.toJson(_history.value))
            .putStringSet("resolved", _resolved.value.map { it.toString() }.toSet())
            .apply()
    }

    private fun loadHistory(): List<PaperTrade> {
        val json = prefs.getString("history", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PaperTrade>>() {}.type
            gson.fromJson<List<PaperTrade>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadResolved(): Set<Long> =
        (prefs.getStringSet("resolved", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    private fun loadSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            twelveKey = prefs.getString("twelveKey", "") ?: "",
            marketAuxKey = prefs.getString("marketAuxKey", "") ?: "",
            fredKey = prefs.getString("fredKey", "") ?: "",
            aiKey = prefs.getString("aiKey", "") ?: "",
            baseUrl = prefs.getString("baseUrl", d.baseUrl) ?: d.baseUrl,
            model = prefs.getString("model", d.model) ?: d.model
        )
    }

    private fun log(message: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _logs.value = (listOf("[$ts] $message") + _logs.value).take(100)
    }
}
