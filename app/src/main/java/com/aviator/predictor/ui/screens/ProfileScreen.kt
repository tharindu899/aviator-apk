package com.aviator.predictor.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviator.predictor.data.models.*
import com.aviator.predictor.ui.AppUiState
import com.aviator.predictor.ui.components.*
import com.aviator.predictor.ui.theme.*
import com.aviator.predictor.utils.Formatters

@Composable
fun ProfileScreen(
    state: AppUiState,
    onSaveProfile: (UserProfile) -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember(state.user) { mutableStateOf(state.user?.displayName ?: "") }
    var editingName by remember { mutableStateOf(false) }
    var settings by remember(state.settings) { mutableStateOf(state.settings) }
    var showSignOutDialog by remember { mutableStateOf(false) }

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
                UserAvatar(
                    photoUrl = state.user?.photoUrl,
                    displayName = state.user?.displayName ?: "",
                    size = 80.dp
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (editingName) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPurple,
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = BrandPurple
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                editingName = false
                                displayName = state.user?.displayName ?: ""
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Cancel", color = Color(0xFF94A3B8)) }
                        Button(
                            onClick = {
                                state.user?.let { u ->
                                    onSaveProfile(u.copy(displayName = displayName))
                                }
                                editingName = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                        ) { Text("Save") }
                    }
                } else {
                    Text(
                        state.user?.displayName ?: "User",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        state.user?.email ?: "",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { editingName = true },
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandPurple)
                    ) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Name", fontSize = 13.sp)
                    }
                }
            }
        }

        // ── Account info ───────────────────────────────────────────────────
        GradientCard {
            Text("Account Information", color = BrandPurple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            listOf(
                Triple(Icons.Filled.CalendarToday, "Member Since",
                    Formatters.formatDate(state.user?.createdAt ?: System.currentTimeMillis())),
                Triple(Icons.Filled.Login, "Last Login",
                    Formatters.formatDate(state.user?.lastLoginAt ?: System.currentTimeMillis())),
                Triple(Icons.Filled.SignalCellularAlt, "Total Signals",
                    state.user?.signalCount?.toString() ?: "0"),
                Triple(Icons.Filled.EmojiEvents, "Total Wins",
                    state.user?.totalWins?.toString() ?: "0")
            ).forEach { (icon, label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = BrandPurple.copy(0.7f), modifier = Modifier.size(18.dp))
                        Text(label, color = Color(0xFF94A3B8), fontSize = 14.sp)
                    }
                    Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)
            }
        }

        // ── Settings ───────────────────────────────────────────────────────
        GradientCard {
            Text("App Settings", color = BrandPurple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))

            SettingToggle(
                icon = Icons.Filled.Notifications,
                label = "Notifications",
                description = "Signal alerts",
                checked = settings.notifications
            ) {
                settings = settings.copy(notifications = !settings.notifications)
                onSaveSettings(settings)
            }
            SettingToggle(
                icon = Icons.Filled.VolumeUp,
                label = "Sound Effects",
                description = "Play sounds for alerts",
                checked = settings.sound
            ) {
                settings = settings.copy(sound = !settings.sound)
                onSaveSettings(settings)
            }
            SettingToggle(
                icon = Icons.Filled.AlarmOff,
                label = "Auto-mark Missed",
                description = "Mark expired signals automatically",
                checked = settings.autoMarkMissed
            ) {
                settings = settings.copy(autoMarkMissed = !settings.autoMarkMissed)
                onSaveSettings(settings)
            }
            SettingToggle(
                icon = Icons.Filled.Timer,
                label = "Show Countdown",
                description = "Live countdown timers",
                checked = settings.showCountdown
            ) {
                settings = settings.copy(showCountdown = !settings.showCountdown)
                onSaveSettings(settings)
            }
        }

        // ── Sync status ────────────────────────────────────────────────────
        GradientCard {
            Text("Data & Sync", color = BrandPurple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CloudSync, null, tint = BrandPurple, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Google Drive", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("All data saved privately", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                }
                SyncStatusPill(state.syncStatus)
            }
        }

        // ── Sign out ───────────────────────────────────────────────────────
        Button(
            onClick = { showSignOutDialog = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedClosed.copy(alpha = 0.15f)),
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
            containerColor = SlateSurface,
            title = { Text("Sign Out?", color = Color.White) },
            text = { Text("Your data stays saved in Google Drive.", color = Color(0xFF94A3B8)) },
            confirmButton = {
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

@Composable
private fun SettingToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandPurple.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = BrandPurple, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(label, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(description, color = Color(0xFF64748B), fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GreenActive,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF475569)
            )
        )
    }
}
