package com.zelsignals.app.data

import kotlin.math.max
import kotlin.math.sqrt

object StrategyEngine {

    /** Exponential moving average. Returns a list the same length as [prices]. */
    fun calculateEMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty() || period <= 0) return emptyList()
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double>(prices.size)
        var prev = prices[0]
        out.add(prev)
        for (i in 1 until prices.size) {
            prev = prices[i] * k + prev * (1 - k)
            out.add(prev)
        }
        return out
    }

    /** Wilder's RSI, returns latest value. */
    fun calculateRSI(prices: List<Double>, period: Int = 14): Double {
        if (period <= 0 || prices.size <= period) return 50.0
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = prices[i] - prices[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        for (i in (period + 1) until prices.size) {
            val d = prices[i] - prices[i - 1]
            avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
        }
        if (avgLoss == 0.0) return if (avgGain == 0.0) 50.0 else 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    /** Returns Pair(upper, lower) using 2 standard deviations. */
    fun calculateBollingerBands(prices: List<Double>, period: Int = 20): Pair<Double, Double> {
        if (prices.isEmpty()) return Pair(0.0, 0.0)
        val window = prices.takeLast(period)
        val mean = window.average()
        val variance = window.sumOf { (it - mean) * (it - mean) } / window.size
        val sd = sqrt(variance)
        return Pair(mean + 2 * sd, mean - 2 * sd)
    }

    /** Returns Pair(MACD line, Signal line) using 12/26/9. */
    fun calculateMACD(prices: List<Double>): Pair<Double, Double> {
        if (prices.size < 2) return Pair(0.0, 0.0)
        val ema12 = calculateEMA(prices, 12)
        val ema26 = calculateEMA(prices, 26)
        val macdLine = ema12.indices.map { ema12[it] - ema26[it] }
        val signalLine = calculateEMA(macdLine, 9)
        return Pair(macdLine.last(), signalLine.last())
    }

    /** Combines EMA trend, RSI, Bollinger and MACD into "Bullish", "Bearish" or "Neutral". */
    fun generateStrategySignal(prices: List<Double>): String {
        if (prices.size < 30) return "Neutral"
        val price = prices.last()
        var score = 0

        val ema20 = calculateEMA(prices, 20).last()
        val ema50 = calculateEMA(prices, 50).last()
        score += if (ema20 > ema50) 1 else -1
        if (prices.size >= 200) {
            val ema200 = calculateEMA(prices, 200).last()
            score += if (price > ema200) 1 else -1
        }

        val rsi = calculateRSI(prices)
        if (rsi < 30) score += 1 else if (rsi > 70) score -= 1

        val (upper, lower) = calculateBollingerBands(prices)
        if (price < lower) score += 1 else if (price > upper) score -= 1

        val (macd, signal) = calculateMACD(prices)
        score += if (macd > signal) 1 else -1

        return when {
            score >= 2 -> "Bullish"
            score <= -2 -> "Bearish"
            else -> "Neutral"
        }
    }
}
