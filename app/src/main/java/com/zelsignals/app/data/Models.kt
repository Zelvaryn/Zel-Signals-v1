package com.zelsignals.app.data

data class TradeSignal(
    val bias: String,
    val confidence: Int,
    val entry: Double,
    val takeProfit: Double,
    val stopLoss: Double,
    val positionSizePct: Double,
    val regime: String,
    val reasoning: String,
    val risks: List<String>
)

data class NewsItem(
    val headline: String,
    val sentiment: String,
    val source: String
)

data class MacroData(
    val indicator: String,
    val value: String,
    val date: String
)

data class PaperTrade(
    val symbol: String,
    val bias: String,
    val entry: Double,
    val isWin: Boolean,
    val timestamp: Long
)
