package com.aviator.predictor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviator.predictor.data.models.*
import com.aviator.predictor.ui.components.*
import com.aviator.predictor.ui.theme.*
import com.aviator.predictor.utils.Formatters
import com.aviator.predictor.utils.TimeCalculations

@Composable
fun ResultsScreen(
    signals: List<Signal>,
    onMarkWin: (String) -> Unit,
    onMarkLoss: (String) -> Unit,
    onReset: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var filter      by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    val filters = listOf("all", "pending", "win", "loss", "missed")
    val filterCounts = filters.associateWith { f ->
        if (f == "all") signals.size
        else signals.count { it.status.name.lowercase() == f }
    }

    val filtered = signals
        .filter { if (filter == "all") true else it.status.name.lowercase() == filter }
        .filter {
            if (searchQuery.isBlank()) true
            else {
                val q = searchQuery.lowercase()
                it.odd.toString().contains(q) ||
                it.status.name.lowercase().contains(q) ||
                Formatters.formatTime(it.resultTime).contains(q)
            }
        }
        .sortedByDescending { it.createdAt }

    val wins      = signals.count { it.status == SignalStatus.WIN }
    val losses    = signals.count { it.status == SignalStatus.LOSS }
    val completed = wins + losses
    val winRate   = if (completed > 0) (wins * 100) / completed else 0

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Stats strip ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("Total", signals.size, BrandPurple),
                Triple("Won",   wins,         GreenActive),
                Triple("Lost",  losses,        RedClosed),
                Triple("Win%",  winRate,       BlueInfo)
            ).forEach { (label, value, color) ->
                Card(
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(label, color = color.copy(alpha = 0.8f), fontSize = 10.sp)
                        Text(
                            if (label == "Win%") "$value%" else value.toString(),
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp
                        )
                    }
                }
            }
        }

        // ── Filter chips — single row, horizontal scroll, no wrap ─────────
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())   // ← scrollable, not wrapping
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            filters.forEach { f ->
                val selected = filter == f
                FilterChip(
                    selected = selected,
                    onClick  = { filter = f },
                    label    = {
                        Text(
                            "${f.replaceFirstChar { it.uppercase() }} (${filterCounts[f]})",
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    },
                    shape  = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandPurple.copy(alpha = 0.3f),
                        selectedLabelColor     = BrandPurple,
                        containerColor         = Color(0xFF1E293B),
                        labelColor             = Color(0xFFCBD5E1)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled       = true,
                        selected      = selected,
                        borderColor   = if (selected) BrandPurple else Color(0xFF334155),
                        selectedBorderColor = BrandPurple
                    )
                )
            }
        }

        // ── Search ────────────────────────────────────────────────────────
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = { searchQuery = it },
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder   = {
                Text("Search by odd, status, time…", color = Color(0xFF64748B), fontSize = 13.sp)
            },
            leadingIcon  = { Icon(Icons.Filled.Search, null, tint = Color(0xFF94A3B8)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, null, tint = Color(0xFF94A3B8))
                    }
                }
            },
            singleLine = true,
            colors     = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = BrandPurple,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White
            ),
            shape = RoundedCornerShape(14.dp)
        )

        // ── Empty state ───────────────────────────────────────────────────
        if (signals.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.BarChart, null,
                        tint     = BrandPurple.copy(0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No results yet", color = Color(0xFF94A3B8), fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Showing ${filtered.size} of ${signals.size}",
                        color    = Color(0xFF64748B),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(filtered, key = { it.id }) { signal ->
                    ResultCard(
                        signal     = signal,
                        onMarkWin  = onMarkWin,
                        onMarkLoss = onMarkLoss,
                        onReset    = onReset,
                        onDelete   = { deleteTarget = signal.id }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────
    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = SlateSurface,
            title            = { Text("Delete Signal?", color = Color.White) },
            text             = { Text("This cannot be undone.", color = Color(0xFF94A3B8)) },
            confirmButton    = {
                TextButton(onClick = { onDelete(id); deleteTarget = null }) {
                    Text("Delete", color = RedClosed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

// ── Result card ───────────────────────────────────────────────────────────────

@Composable
private fun ResultCard(
    signal: Signal,
    onMarkWin: (String) -> Unit,
    onMarkLoss: (String) -> Unit,
    onReset: (String) -> Unit,
    onDelete: () -> Unit
) {
    val countdown = if (signal.status == SignalStatus.PENDING)
        TimeCalculations.getCountdown(signal) else null
    val isActive  = countdown != null && countdown > -45 && countdown <= 45

    val borderColor = when {
        isActive                          -> GreenActive
        signal.status == SignalStatus.WIN    -> GreenActive.copy(alpha = 0.4f)
        signal.status == SignalStatus.LOSS   -> RedClosed.copy(alpha = 0.4f)
        signal.status == SignalStatus.MISSED -> Color(0xFF475569)
        else                              -> BrandPurple.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x661E293B))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Time + status row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        Formatters.formatTime(signal.resultTime),
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 20.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Text("${String.format("%.2f", signal.odd)}x", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    SignalStatusChip(signal.status)
                    if (isActive && countdown != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${kotlin.math.abs(countdown)}s",
                            color = GreenActive, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(Formatters.formatDateTime(signal.createdAt), color = Color(0xFF64748B), fontSize = 11.sp)

            // Window times
            val wt = signal.windowTimes ?: TimeCalculations.getBetWindowTimes(signal.resultTime)
            Row(
                modifier              = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WindowTimeCell("OPEN",  Formatters.formatTime(wt.openTime))
                WindowTimeCell("CLOSE", Formatters.formatTime(wt.closeTime))
            }

            // Active badge
            if (isActive) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(GreenActive.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ACTIVE NOW", color = GreenActive,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier   = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Actions
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (signal.status == SignalStatus.PENDING) {
                    OutlinedButton(
                        onClick  = { onMarkWin(signal.id) },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = GreenActive)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Win", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick  = { onMarkLoss(signal.id) },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedClosed)
                    ) {
                        Icon(Icons.Filled.Cancel, null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Loss", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick  = { onReset(signal.id) },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = BrandPurple)
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontSize = 12.sp)
                    }
                }
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(RedClosed.copy(alpha = 0.1f))
                        .size(40.dp)
                ) {
                    Icon(Icons.Filled.Delete, null, tint = RedClosed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun WindowTimeCell(label: String, time: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x33334155))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                time, color = Color.White, fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
