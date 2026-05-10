package com.aviator.predictor.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aviator.predictor.ui.UpdateDownloadState
import com.aviator.predictor.ui.theme.*
import com.aviator.predictor.utils.UpdateInfo

// ── Helper: format ETA nicely ─────────────────────────────────────────────────

private fun formatEta(seconds: Int): String = when {
    seconds <= 0  -> ""
    seconds < 60  -> "${seconds}s left"
    else          -> "${seconds / 60}m ${seconds % 60}s left"
}

private fun formatMb(mb: Float): String = String.format("%.1f MB", mb)

// ── Main update dialog ────────────────────────────────────────────────────────

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    downloadState: UpdateDownloadState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (downloadState !is UpdateDownloadState.Downloading) onDismiss()
        },
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E293B))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(BrandPurple, BrandPink)),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // ── Header ────────────────────────────────────────────────
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(BrandPurple, BrandPink))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint     = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column {
                        Text(
                            "Update Available",
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp
                        )
                        Text(
                            "${updateInfo.currentVersion}  →  ${updateInfo.latestVersion}",
                            color    = BrandPurple,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Release notes ─────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33334155))
                        .padding(12.dp)
                ) {
                    Text(
                        updateInfo.releaseNotes,
                        color    = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Action area ───────────────────────────────────────────
                when (downloadState) {

                    is UpdateDownloadState.Idle -> {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick  = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF94A3B8)
                                )
                            ) { Text("Later") }

                            Button(
                                onClick  = onDownload,
                                modifier = Modifier.weight(2f),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                            ) {
                                Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download & Install", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateDownloadState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                            // Top row: label + percentage
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Downloading update…",
                                    color      = Color(0xFF94A3B8),
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${downloadState.percent}%",
                                    color      = BrandPurple,
                                    fontSize   = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Progress bar
                            LinearProgressIndicator(
                                progress   = { downloadState.percent / 100f },
                                modifier   = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color      = BrandPurple,
                                trackColor = Color(0x33A855F7)
                            )

                            // Size + ETA row
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                // Downloaded / total size
                                if (downloadState.totalMb > 0.1f) {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.CloudDownload, null,
                                            tint     = Color(0xFF64748B),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            "${formatMb(downloadState.downloadedMb)} / ${formatMb(downloadState.totalMb)}",
                                            color    = Color(0xFF64748B),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                } else {
                                    Text(
                                        "Calculating size…",
                                        color    = Color(0xFF64748B),
                                        fontSize = 11.sp
                                    )
                                }

                                // ETA
                                if (downloadState.etaSeconds > 0) {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Timer, null,
                                            tint     = OrangeWaiting,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            formatEta(downloadState.etaSeconds),
                                            color      = OrangeWaiting,
                                            fontSize   = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Speed chip
                            if (downloadState.speedKbps > 1f) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BrandPurple.copy(alpha = 0.1f))
                                        .border(1.dp, BrandPurple.copy(0.2f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        if (downloadState.speedKbps >= 1024f)
                                            String.format("%.1f MB/s", downloadState.speedKbps / 1024f)
                                        else
                                            String.format("%.0f KB/s", downloadState.speedKbps),
                                        color      = BrandPurple,
                                        fontSize   = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Text(
                                "Please keep the app open",
                                color    = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GreenActive.copy(alpha = 0.12f))
                                    .padding(10.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, null, tint = GreenActive, modifier = Modifier.size(18.dp))
                                Text(
                                    "Download complete — tap Install to update",
                                    color = GreenActive, fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick  = onInstall,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = GreenActive)
                            ) {
                                Icon(Icons.Filled.InstallMobile, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }

                    is UpdateDownloadState.Failed -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RedClosed.copy(alpha = 0.12f))
                                    .padding(10.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = RedClosed, modifier = Modifier.size(18.dp))
                                Text("Download failed. Check your connection.", color = RedClosed, fontSize = 13.sp)
                            }
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick  = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(12.dp),
                                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                                ) { Text("Cancel") }
                                Button(
                                    onClick  = onDownload,
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(12.dp),
                                    colors   = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                                ) { Text("Retry") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Update banner ─────────────────────────────────────────────────────────────

@Composable
fun UpdateBanner(latestVersion: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(BrandPurple.copy(alpha = 0.9f), BrandPink.copy(alpha = 0.9f))))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.NewReleases, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    "New version $latestVersion available!",
                    color      = Color.White,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onClick) {
                Text("Update", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
