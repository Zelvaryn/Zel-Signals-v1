package com.zelsignals.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zelsignals.app.data.MacroData
import com.zelsignals.app.data.NewsItem
import com.zelsignals.app.data.PaperTrade
import com.zelsignals.app.data.TradeSignal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val BgColor = Color(0xFF0B0E14)
private val CardColor = Color(0xFF151A24)
private val BorderColor = Color(0xFF232A38)
private val GreenColor = Color(0xFF00C087)
private val RedColor = Color(0xFFFF4D5E)
private val AmberColor = Color(0xFFF5A623)
private val MutedColor = Color(0xFF8A94A6)
private val TextColor = Color(0xFFE6EAF2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
          val premiumColors = darkColorScheme(
              primary = Color(0xFF00E676),
              onPrimary = Color.Black,
              secondary = Color(0xFF9D4EDD),
              background = Color(0xFF070A11),
              surface = Color(0xFF121824),
              onBackground = Color(0xFFF8F9FA),
              onSurface = Color(0xFFF8F9FA),
              error = Color(0xFFFF3B30),
              )

          MaterialTheme(colorScheme = premiumColors){
                ZelApp()
            }
        }
    }
}

private fun biasColor(bias: String): Color = when (bias) {
    "Bullish", "Positive" -> GreenColor
    "Bearish", "Negative" -> RedColor
    else -> AmberColor
}

private fun fmtPrice(v: Double): String = if (abs(v) >= 100) String.format(Locale.US, "%.2f", v) else String.format(Locale.US, "%.5f", v)

@Composable
fun ZelApp() {
    val vm: SignalsViewModel = viewModel()
    var tab by rememberSaveable { mutableStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val titles = listOf("Dashboard", "Journal", "News", "Macro", "Strategies")

    Column(
        Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 28.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("ZEL SIGNALS PRO", color = GreenColor, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
                Text("AI-assisted trading terminal", color = MutedColor, fontSize = 11.sp)
            }
            TextButton(onClick = { showSettings = !showSettings }) {
                Text(if (showSettings) "Close" else "Settings", color = if (showSettings) AmberColor else TextColor)
            }
        }

        if (showSettings) {
            SettingsScreen(vm)
        } else {
            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = BgColor,
                contentColor = GreenColor,
                edgePadding = 8.dp
            ) {
                titles.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(t, color = if (tab == i) GreenColor else MutedColor, fontSize = 13.sp) }
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> DashboardScreen(vm)
                    1 -> JournalScreen(vm)
                    2 -> NewsScreen(vm)
                    3 -> MacroScreen(vm)
                    else -> StrategiesScreen(vm)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ shared

@Composable
private fun TerminalCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = TextColor) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MutedColor, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MutedColor, modifier = Modifier.padding(32.dp))
    }
}

// --------------------------------------------------------------- dashboard

@Composable
fun DashboardScreen(vm: SignalsViewModel) {
    val symbol by vm.symbol.collectAsState()
    val timeframe by vm.timeframe.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val signal by vm.currentSignal.collectAsState()
    val history by vm.history.collectAsState()
    val resolved by vm.resolved.collectAsState()
    val logs by vm.logs.collectAsState()
    val settings by vm.settings.collectAsState()
    val strategy by vm.strategySignal.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    val timeframes = listOf("1min", "5min", "15min", "30min", "1h", "4h", "1day")
    val winRate = remember(history, resolved) { vm.calculateWinRate() }
    val closedCount = remember(history, resolved) { history.count { it.timestamp in resolved } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TerminalCard {
            OutlinedTextField(
                value = symbol,
                onValueChange = { vm.setSymbol(it) },
                label = { Text("Symbol (e.g. EUR/USD, AAPL, BTC/USD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Box {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Timeframe: $timeframe", color = TextColor)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    timeframes.forEach { tf ->
                        DropdownMenuItem(
                            text = { Text(tf) },
                            onClick = {
                                vm.setTimeframe(tf)
                                menuOpen = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    vm.analyzeMarket(
                        settings.twelveKey, settings.marketAuxKey, settings.fredKey,
                        settings.aiKey, settings.baseUrl, settings.model
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenColor, contentColor = Color.Black)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Analyzing...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Analyze & Generate Signal", fontWeight = FontWeight.Bold)
                }
            }
        }

        TerminalCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PAPER WIN RATE", color = MutedColor, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text("$closedCount closed trades", color = MutedColor, fontSize = 12.sp)
                }
                Text(
                    "$winRate%",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = if (closedCount == 0) MutedColor else if (winRate >= 50) GreenColor else RedColor
                )
            }
        }

        signal?.let { s ->
            SignalCard(s, strategy) {
                vm.savePaperTrade(
                    PaperTrade(
                        symbol = symbol.trim().uppercase(Locale.US),
                        bias = s.bias,
                        entry = s.entry,
                        isWin = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        if (logs.isNotEmpty()) {
            TerminalCard {
                Text("ACTIVITY LOG", color = MutedColor, fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                logs.take(8).forEach {
                    Text(it, color = MutedColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SignalCard(s: TradeSignal, strategy: String, onSave: () -> Unit) {
    val c = biasColor(s.bias)
    TerminalCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("AI SIGNAL", color = MutedColor, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(s.bias.uppercase(), color = c, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("CONFIDENCE", color = MutedColor, fontSize = 11.sp, letterSpacing = 1.sp)
                Text("${s.confidence}%", color = TextColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { s.confidence / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = c,
            trackColor = BorderColor
        )
        Spacer(Modifier.height(12.dp))
        StatRow("Entry", fmtPrice(s.entry))
        StatRow("Take Profit", fmtPrice(s.takeProfit), GreenColor)
        StatRow("Stop Loss", fmtPrice(s.stopLoss), RedColor)
        StatRow("Position Size", String.format(Locale.US, "%.2f%% of portfolio", s.positionSizePct), AmberColor)
        StatRow("Market Regime", s.regime)
        StatRow("Technical Strategy", strategy, biasColor(strategy))
        Spacer(Modifier.height(10.dp))
        Text("REASONING", color = MutedColor, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(s.reasoning.ifBlank { "-" }, color = TextColor, fontSize = 13.sp, lineHeight = 19.sp)
        if (s.risks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("RISKS", color = MutedColor, fontSize = 11.sp, letterSpacing = 1.sp)
            s.risks.forEach { Text("• $it", color = AmberColor, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("Save to Journal (Paper Trade)", color = TextColor)
        }
    }
}

// ----------------------------------------------------------------- journal

@Composable
fun JournalScreen(vm: SignalsViewModel) {
    val history by vm.history.collectAsState()
    val resolved by vm.resolved.collectAsState()
    val winRate = remember(history, resolved) { vm.calculateWinRate() }
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Win Rate: $winRate%",
            color = if (winRate >= 50) GreenColor else RedColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        if (history.isEmpty()) {
            EmptyState("No paper trades yet. Generate a signal and save it to your journal.")
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.timestamp }) { t ->
                    val done = t.timestamp in resolved
                    TerminalCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(t.symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(fmt.format(Date(t.timestamp)), color = MutedColor, fontSize = 11.sp)
                            }
                            Text(t.bias.uppercase(), color = biasColor(t.bias), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        StatRow("Entry", fmtPrice(t.entry))
                        StatRow(
                            "Status",
                            if (!done) "OPEN" else if (t.isWin) "WIN" else "LOSS",
                            if (!done) AmberColor else if (t.isWin) GreenColor else RedColor
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.markTrade(t, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenColor, contentColor = Color.Black),
                                modifier = Modifier.weight(1f)
                            ) { Text("Win") }
                            Button(
                                onClick = { vm.markTrade(t, false) },
                                colors = ButtonDefaults.buttonColors(containerColor = RedColor, contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) { Text("Loss") }
                            TextButton(onClick = { vm.deleteTrade(t) }) { Text("Delete", color = MutedColor) }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------- news

@Composable
fun NewsScreen(vm: SignalsViewModel) {
    val news by vm.currentNews.collectAsState()
    if (news.isEmpty()) {
        EmptyState("No news loaded. Run an analysis (MarketAux key required).")
        return
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(news) { n -> NewsRow(n) }
    }
}

@Composable
private fun NewsRow(n: NewsItem) {
    val c = biasColor(n.sentiment)
    TerminalCard {
        Text(n.sentiment.uppercase(), color = c, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(n.headline, color = TextColor, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(n.source, color = MutedColor, fontSize = 11.sp)
    }
}

// ------------------------------------------------------------------- macro

@Composable
fun MacroScreen(vm: SignalsViewModel) {
    val macro by vm.currentMacro.collectAsState()
    if (macro.isEmpty()) {
        EmptyState("No macro data loaded. Run an analysis (FRED key required).")
        return
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(macro) { m -> MacroRow(m) }
    }
}

@Composable
private fun MacroRow(m: MacroData) {
    TerminalCard {
        Text(m.indicator, color = MutedColor, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            m.value,
            color = if (m.value == "Unavailable") RedColor else TextColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(if (m.date.isBlank()) "" else "As of ${m.date}", color = MutedColor, fontSize = 11.sp)
    }
}

// -------------------------------------------------------------- strategies

@Composable
fun StrategiesScreen(vm: SignalsViewModel) {
    val signal by vm.strategySignal.collectAsState()
    val ind by vm.indicators.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TerminalCard {
            Text("COMBINED STRATEGY SIGNAL", color = MutedColor, fontSize = 11.sp, letterSpacing = 1.sp)
            Text(signal.uppercase(), color = biasColor(signal), fontSize = 28.sp, fontWeight = FontWeight.Black)
            if (ind == null) Text("Run an analysis to compute indicators.", color = MutedColor, fontSize = 12.sp)
        }

        TerminalCard {
            Text("EMA Trend (20 / 50 / 200)", fontWeight = FontWeight.Bold)
            Text("Fast EMA above slow EMA and price above EMA200 signals an uptrend.", color = MutedColor, fontSize = 12.sp)
            ind?.let {
                Spacer(Modifier.height(6.dp))
                StatRow("EMA 20", fmtPrice(it.ema20))
                StatRow("EMA 50", fmtPrice(it.ema50))
                StatRow("EMA 200", fmtPrice(it.ema200))
                StatRow("Trend", if (it.ema20 > it.ema50) "Bullish" else "Bearish", biasColor(if (it.ema20 > it.ema50) "Bullish" else "Bearish"))
            }
        }

        TerminalCard {
            Text("Bollinger Bands (20, 2σ)", fontWeight = FontWeight.Bold)
            Text("Price outside the bands suggests an overextended move (mean reversion).", color = MutedColor, fontSize = 12.sp)
            ind?.let {
                Spacer(Modifier.height(6.dp))
                StatRow("Upper", fmtPrice(it.bbUpper))
                StatRow("Lower", fmtPrice(it.bbLower))
                val state = when {
                    it.price > it.bbUpper -> "Overbought"
                    it.price < it.bbLower -> "Oversold"
                    else -> "Inside bands"
                }
                StatRow("State", state, if (state == "Overbought") RedColor else if (state == "Oversold") GreenColor else AmberColor)
            }
        }

        TerminalCard {
            Text("RSI (14)", fontWeight = FontWeight.Bold)
            Text("Below 30 is oversold, above 70 is overbought.", color = MutedColor, fontSize = 12.sp)
            ind?.let {
                Spacer(Modifier.height(6.dp))
                StatRow(
                    "RSI",
                    String.format(Locale.US, "%.1f", it.rsi),
                    if (it.rsi > 70) RedColor else if (it.rsi < 30) GreenColor else TextColor
                )
            }
        }

        TerminalCard {
            Text("MACD (12, 26, 9)", fontWeight = FontWeight.Bold)
            Text("MACD line crossing above the signal line indicates bullish momentum.", color = MutedColor, fontSize = 12.sp)
            ind?.let {
                Spacer(Modifier.height(6.dp))
                StatRow("MACD", String.format(Locale.US, "%.5f", it.macd))
                StatRow("Signal", String.format(Locale.US, "%.5f", it.macdSignal))
                val bull = it.macd > it.macdSignal
                StatRow("Momentum", if (bull) "Bullish" else "Bearish", biasColor(if (bull) "Bullish" else "Bearish"))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(vm: SignalsViewModel) {
    val saved by vm.settings.collectAsState()
    var draft by remember(saved) { mutableStateOf(saved) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("API Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Keys are stored locally on this device.", color = MutedColor, fontSize = 12.sp)

        OutlinedTextField(
            value = draft.twelveKey, onValueChange = { draft = draft.copy(twelveKey = it) },
            label = { Text("Twelve Data Key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.marketAuxKey, onValueChange = { draft = draft.copy(marketAuxKey = it) },
            label = { Text("MarketAux Key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.fredKey, onValueChange = { draft = draft.copy(fredKey = it) },
            label = { Text("FRED Key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.aiKey, onValueChange = { draft = draft.copy(aiKey = it) },
            label = { Text("AI Key (Groq / OpenRouter)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.baseUrl, onValueChange = { draft = draft.copy(baseUrl = it) },
            label = { Text("AI Base URL") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.model, onValueChange = { draft = draft.copy(model = it) },
            label = { Text("AI Model") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { draft = draft.copy(baseUrl = "https://api.groq.com/openai/v1", model = "llama-3.3-70b-versatile") },
                modifier = Modifier.weight(1f)
            ) { Text("Groq preset", color = TextColor, fontSize = 12.sp) }
            OutlinedButton(
                onClick = { draft = draft.copy(baseUrl = "https://openrouter.ai/api/v1", model = "meta-llama/llama-3.3-70b-instruct") },
                modifier = Modifier.weight(1f)
            ) { Text("OpenRouter preset", color = TextColor, fontSize = 12.sp) }
        }

        Button(
            onClick = { vm.updateSettings(draft) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenColor, contentColor = Color.Black)
        ) { Text("Save Settings", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(16.dp))
    }
}
