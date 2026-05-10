package com.aviator.predictor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aviator.predictor.data.models.*
import com.aviator.predictor.ui.theme.*
import com.aviator.predictor.utils.Formatters

// ── Gradient Card ─────────────────────────────────────────────────────────────

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    borderColor: Color = BrandPurple.copy(alpha = 0.3f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x80334155))
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

// ── Bet window status badge ────────────────────────────────────────────────────

@Composable
fun StatusBadge(status: BetWindowStatus) {
    val (label, color) = when (status) {
        BetWindowStatus.WAITING -> "WAITING" to OrangeWaiting
        BetWindowStatus.ACTIVE -> "BET NOW" to GreenActive
        BetWindowStatus.GRACE -> "GRACE" to YellowGrace
        BetWindowStatus.CLOSED -> "CLOSED" to RedClosed
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Signal status chip ────────────────────────────────────────────────────────

@Composable
fun SignalStatusChip(status: SignalStatus) {
    val (label, color) = when (status) {
        SignalStatus.WIN -> "WON" to GreenActive
        SignalStatus.LOSS -> "LOST" to RedClosed
        SignalStatus.MISSED -> "MISSED" to Color(0xFF94A3B8)
        SignalStatus.PENDING -> "PENDING" to BlueInfo
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ── User avatar (shows photo or initials) ────────────────────────────────────

@Composable
fun UserAvatar(
    photoUrl: String?,
    displayName: String,
    size: Dp = 40.dp
) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "Profile photo",
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(Brush.linearGradient(listOf(BrandPurple, BrandPink))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = Formatters.getInitials(displayName),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.35f).sp
            )
        }
    }
}

// ── Sync status pill ──────────────────────────────────────────────────────────

@Composable
fun SyncStatusPill(status: com.aviator.predictor.ui.SyncStatus) {
    val (label, color) = when (status) {
        com.aviator.predictor.ui.SyncStatus.SYNCED -> "Online" to GreenActive
        com.aviator.predictor.ui.SyncStatus.SYNCING -> "Syncing" to BlueInfo
        com.aviator.predictor.ui.SyncStatus.OFFLINE -> "Offline" to OrangeWaiting
        com.aviator.predictor.ui.SyncStatus.ERROR -> "Error" to RedClosed
        com.aviator.predictor.ui.SyncStatus.PENDING -> "Loading" to Color(0xFF94A3B8)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, count: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        count?.let {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandPurple.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("$it", color = BrandPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
