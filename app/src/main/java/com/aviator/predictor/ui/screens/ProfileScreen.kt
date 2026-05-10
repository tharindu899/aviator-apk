package com.aviator.predictor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviator.predictor.BuildConfig
import com.aviator.predictor.data.models.*
import com.aviator.predictor.ui.AppUiState
import com.aviator.predictor.ui.components.*
import com.aviator.predictor.ui.theme.*
import com.aviator.predictor.utils.Formatters

private const val GITHUB_URL    = "https://github.com/tharindu899"
private const val PORTFOLIO_URL = "http://web.toxybox99.eu.org/"
private const val GITHUB_REPO   = "https://github.com/tharindu899/aviator-apk"

@Composable
fun ProfileScreen(
    state: AppUiState,
    onSaveProfile: (UserProfile) -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    var displayName       by remember(state.user) { mutableStateOf(state.user?.displayName ?: "") }
    var editingName       by remember { mutableStateOf(false) }
    var settings          by remember(state.settings) { mutableStateOf(state.settings) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    // Live stats
    val totalSignals = state.signals.size
    val totalWins    = state.signals.count { it.status == SignalStatus.WIN }
    val totalLosses  = state.signals.count { it.status == SignalStatus.LOSS }
    val totalMissed  = state.signals.count { it.status == SignalStatus.MISSED }
    val completed    = totalWins + totalLosses
    val winRate      = if (completed > 0) (totalWins * 100) / completed else 0

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Profile header ─────────────────────────────────────────────────
        GradientCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                UserAvatar(photoUrl = state.user?.photoUrl, displayName = state.user?.displayName ?: "", size = 80.dp)
                Spacer(modifier = Modifier.height(12.dp))

                if (editingName) {
                    OutlinedTextField(
                        value         = displayName,
                        onValueChange = { displayName = it },
                        label         = { Text("Display Name") },
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = BrandPurple,
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White,
                            focusedLabelColor    = BrandPurple
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { editingName = false; displayName = state.user?.displayName ?: "" },
                            shape   = RoundedCornerShape(10.dp)
                        ) { Text("Cancel", color = Color(0xFF94A3B8)) }
                        Button(
                            onClick = {
                                state.user?.let { u -> onSaveProfile(u.copy(displayName = displayName)) }
                                editingName = false
                            },
                            shape  = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                        ) { Text("Save") }
                    }
                } else {
                    Text(state.user?.displayName ?: "User", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(state.user?.email ?: "", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { editingName = true },
                        shape   = RoundedCornerShape(10.dp),
                        border  = ButtonDefaults.outlinedButtonBorder,
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = BrandPurple)
                    ) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Name", fontSize = 13.sp)
                    }
                }
            }
        }

        // ── Stats grid ─────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Signals", totalSignals.toString(), BrandPurple),
                Triple("Wins",    totalWins.toString(),    GreenActive),
                Triple("Losses",  totalLosses.toString(),  RedClosed),
                Triple("Win %",   "$winRate%",             BlueInfo)
            ).forEach { (label, value, color) ->
                Card(
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(label, color = color.copy(alpha = 0.8f), fontSize = 10.sp)
                        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
            }
        }

        // ── Account info ───────────────────────────────────────────────────
        GradientCard {
            Text("Account Information", color = BrandPurple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            listOf(
                Triple(Icons.Filled.CalendarToday,     "Member Since",  Formatters.formatDate(state.user?.createdAt  ?: System.currentTimeMillis())),
                Triple(Icons.Filled.Login,             "Last Login",    Formatters.formatDate(state.user?.lastLoginAt ?: System.currentTimeMillis())),
                Triple(Icons.Filled.SignalCellularAlt, "Total Signals", totalSignals.toString()),
                Triple(Icons.Filled.EmojiEvents,       "Total Wins",    totalWins.toString()),
                Triple(Icons.Filled.Cancel,            "Total Losses",  totalLosses.toString()),
                Triple(Icons.Filled.NotInterested,     "Missed",        totalMissed.toString())
            ).forEachIndexed { index, (icon, label, value) ->
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = BrandPurple.copy(0.7f), modifier = Modifier.size(18.dp))
                        Text(label, color = Color(0xFF94A3B8), fontSize = 14.sp)
                    }
                    Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                if (index < 5) HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)
            }
        }

        // ── Settings ───────────────────────────────────────────────────────
        GradientCard {
            Text("App Settings", color = BrandPurple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            SettingToggle(Icons.Filled.Notifications, "Notifications", "Signal alerts in notification shade", settings.notifications) {
                settings = settings.copy(notifications = !settings.notifications); onSaveSettings(settings)
            }
            SettingToggle(Icons.Filled.VolumeUp, "Sound Effects", "Play sound when bet window opens", settings.sound) {
                settings = settings.copy(sound = !settings.sound); onSaveSettings(settings)
            }
            SettingToggle(Icons.Filled.AlarmOff, "Auto-mark Missed", "Mark expired signals automatically", settings.autoMarkMissed) {
                settings = settings.copy(autoMarkMissed = !settings.autoMarkMissed); onSaveSettings(settings)
            }
            SettingToggle(Icons.Filled.Timer, "Show Countdown", "Live countdown timers on signals", settings.showCountdown) {
                settings = settings.copy(showCountdown = !settings.showCountdown); onSaveSettings(settings)
            }
        }

        // ── Sync status ────────────────────────────────────────────────────
        GradientCard {
            Text("Data & Sync", color = BrandPurple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudSync, null, tint = BrandPurple, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Google Drive", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("All data saved privately to your account", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                }
                SyncStatusPill(state.syncStatus)
            }
        }

        // ── Developer / About ──────────────────────────────────────────────
        GradientCard {
            Text("Developer", color = BrandPurple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))

            // Developer row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(BrandPurple, BrandPink))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("T", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Column {
                    Text("Tharindu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("@tharindu899", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }

            HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // GitHub button
            LinkButton(
                icon    = Icons.Filled.Code,
                label   = "GitHub",
                subtext = "github.com/tharindu899",
                color   = Color(0xFF6E40C9),
                onClick = { openUrl(GITHUB_URL) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Source repo button
            LinkButton(
                icon    = Icons.Filled.FolderOpen,
                label   = "App Repository",
                subtext = "github.com/tharindu899/aviator-apk",
                color   = Color(0xFF238636),
                onClick = { openUrl(GITHUB_REPO) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Portfolio button
            LinkButton(
                icon    = Icons.Filled.Language,
                label   = "Portfolio",
                subtext = "web.toxybox99.eu.org",
                color   = BlueInfo,
                onClick = { openUrl(PORTFOLIO_URL) }
            )
        }

        // ── App version card ───────────────────────────────────────────────
        GradientCard {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(BrandPurple, BrandPink))),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Info, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Column {
                        Text("Aviator Predictor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Version ${BuildConfig.VERSION_NAME}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                }
                // Version badge pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(BrandPurple.copy(0.3f), BrandPink.copy(0.3f))))
                        .border(1.dp, BrandPurple.copy(0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp
                    )
                }
            }

            if (state.updateInfo != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(OrangeWaiting.copy(alpha = 0.1f))
                        .border(1.dp, OrangeWaiting.copy(0.3f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.SystemUpdateAlt, null, tint = OrangeWaiting, modifier = Modifier.size(16.dp))
                    Text(
                        "v${state.updateInfo.latestVersion} is available",
                        color    = OrangeWaiting,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── Sign out ───────────────────────────────────────────────────────
        Button(
            onClick  = { showSignOutDialog = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = RedClosed.copy(alpha = 0.15f))
        ) {
            Icon(Icons.Filled.Logout, null, tint = RedClosed, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", color = RedClosed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            containerColor   = SlateSurface,
            title            = { Text("Sign Out?", color = Color.White) },
            text             = { Text("Your data stays saved in Google Drive.", color = Color(0xFF94A3B8)) },
            confirmButton    = {
                TextButton(onClick = { showSignOutDialog = false; onSignOut() }) {
                    Text("Sign Out", color = RedClosed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

// ── Link button component ─────────────────────────────────────────────────────

@Composable
private fun LinkButton(
    icon: ImageVector,
    label: String,
    subtext: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
            Column {
                Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtext, color = Color(0xFF64748B), fontSize = 11.sp)
            }
        }
        Icon(Icons.Filled.OpenInNew, null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
    }
}

// ── Setting toggle ─────────────────────────────────────────────────────────────

@Composable
private fun SettingToggle(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.weight(1f)
        ) {
            Box(
                modifier         = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(BrandPurple.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = BrandPurple, modifier = Modifier.size(18.dp)) }
            Column {
                Text(label, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(description, color = Color(0xFF64748B), fontSize = 12.sp)
            }
        }
        Switch(
            checked         = checked,
            onCheckedChange = { onToggle() },
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = GreenActive,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF475569)
            )
        )
    }
}
