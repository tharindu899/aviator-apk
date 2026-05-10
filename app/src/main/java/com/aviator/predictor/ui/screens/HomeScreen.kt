package com.aviator.predictor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviator.predictor.data.models.*
import com.aviator.predictor.ui.AppUiState
import com.aviator.predictor.ui.components.*
import com.aviator.predictor.ui.theme.*
import com.aviator.predictor.utils.Formatters
import com.aviator.predictor.utils.SignalWithWindow

@Composable
fun HomeScreen(
    state: AppUiState,
    onNavigateGenerate: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current signal card (no Win/Loss buttons — use notification or bottom bar)
        item {
            CurrentSignalCard(signal = state.currentSignal)
        }

        // Stats grid
        item {
            val stats = computeStats(state.signals)
            StatsGrid(stats, state.upcomingSignals.size)
        }

        // Upcoming signals
        item {
            SectionHeader("Next Signals", state.upcomingSignals.size)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (state.upcomingSignals.isEmpty()) {
            item { EmptySignalsCard(onNavigateGenerate) }
        } else {
            items(state.upcomingSignals.take(10)) { sw ->
                UpcomingSignalRow(sw)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ── Current signal card ───────────────────────────────────────────────────────

@Composable
private fun CurrentSignalCard(signal: SignalWithWindow?) {
    if (signal == null) {
        GradientCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Icon(
                    Icons.Filled.Timer, null,
                    tint = BrandPurple.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("No Active Signals", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Generate a signal to get started", color = Color(0xFF94A3B8), fontSize = 13.sp)
            }
        }
        return
    }

    val borderColor = when (signal.betWindow.status) {
        BetWindowStatus.ACTIVE  -> GreenActive
        BetWindowStatus.GRACE   -> YellowGrace
        BetWindowStatus.WAITING -> OrangeWaiting
        BetWindowStatus.CLOSED  -> RedClosed
    }

    GradientCard(borderColor = borderColor) {
        // Odd + status / result time + countdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${String.format("%.2f", signal.signal.odd)}x",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                StatusBadge(signal.betWindow.status)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.formatTime(signal.signal.resultTime),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace
                )
                val countdownLabel = when (signal.betWindow.status) {
                    BetWindowStatus.WAITING -> "Opens in ${Formatters.formatCountdown(signal.betWindow.windowOpensIn)}"
                    BetWindowStatus.ACTIVE  -> "Result in ${Formatters.formatCountdown(signal.betWindow.timeUntilResult)}"
                    BetWindowStatus.GRACE   -> "Ends in ${Formatters.formatCountdown(signal.betWindow.windowRemainingTime)}"
                    BetWindowStatus.CLOSED  -> "Expired"
                }
                Text(countdownLabel, color = borderColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress bar
        val progress = when {
            signal.countdown > 45  -> 0f
            signal.countdown > 0   -> (45f - signal.countdown) / 90f
            signal.countdown >= -45 -> 0.5f + (kotlin.math.abs(signal.countdown) / 90f)
            else                   -> 1f
        }.coerceIn(0f, 1f)

        val screenWidthDp = LocalConfiguration.current.screenWidthDp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x33FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(borderColor, borderColor.copy(alpha = 0.7f))))
            )
            // Centre marker at result time
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (screenWidthDp * 0.5f - 32).dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }

        // Window times row
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Open", color = Color(0xFF94A3B8), fontSize = 10.sp)
                Text(
                    Formatters.formatTime(signal.windowTimes.openTime),
                    color = Color.White, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Signal", color = Color(0xFF94A3B8), fontSize = 10.sp)
                Text(
                    Formatters.formatTime(signal.signal.resultTime),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Close", color = Color(0xFF94A3B8), fontSize = 10.sp)
                Text(
                    Formatters.formatTime(signal.windowTimes.closeTime),
                    color = Color.White, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Hint: direct user to the notification shade for Win/Loss
        if ((signal.betWindow.status == BetWindowStatus.ACTIVE ||
             signal.betWindow.status == BetWindowStatus.GRACE) &&
            signal.signal.status == SignalStatus.PENDING) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(borderColor.copy(alpha = 0.08f))
                    .border(1.dp, borderColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Notifications, null,
                    tint = borderColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Tap WIN / LOSS in the notification to mark this signal",
                    color = borderColor.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Stats grid ────────────────────────────────────────────────────────────────

@Composable
private fun StatsGrid(stats: Map<String, Any>, upcomingCount: Int) {
    val items = listOf(
        Triple("Total",    stats["total"] as Int,   BrandPurple),
        Triple("Today",    stats["today"] as Int,   GreenActive),
        Triple("Upcoming", upcomingCount,             OrangeWaiting),
        Triple("Win Rate", "${stats["winRate"]}%",  BlueInfo)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (label, value, color) ->
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(label, color = color.copy(alpha = 0.8f), fontSize = 10.sp)
                    Text(
                        value.toString(),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp
                    )
                }
            }
        }
    }
}

// ── Upcoming row ──────────────────────────────────────────────────────────────

@Composable
private fun UpcomingSignalRow(sw: SignalWithWindow) {
    val borderColor = when (sw.betWindow.status) {
        BetWindowStatus.ACTIVE  -> GreenActive
        BetWindowStatus.GRACE   -> YellowGrace
        BetWindowStatus.WAITING -> OrangeWaiting
        BetWindowStatus.CLOSED  -> Color(0xFF475569)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x661E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    Formatters.formatTime(sw.signal.resultTime),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text("${String.format("%.2f", sw.signal.odd)}x", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(sw.betWindow.status)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    Formatters.formatCountdown(sw.countdown),
                    color = borderColor, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptySignalsCard(onNavigateGenerate: () -> Unit) {
    GradientCard {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(
                Icons.Filled.Timer, null,
                tint = BrandPurple.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("No upcoming signals", color = Color(0xFF94A3B8), fontSize = 15.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onNavigateGenerate,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate Signal")
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun computeStats(signals: List<Signal>): Map<String, Any> {
    val total      = signals.size
    val todayStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0);      set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val today      = signals.count { it.createdAt >= todayStart }
    val wins       = signals.count { it.status == SignalStatus.WIN }
    val losses     = signals.count { it.status == SignalStatus.LOSS }
    val completed  = wins + losses
    val winRate    = if (completed > 0) (wins * 100) / completed else 0
    return mapOf("total" to total, "today" to today, "winRate" to winRate)
}
