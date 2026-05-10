package com.aviator.predictor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviator.predictor.data.models.SignalStatus
import com.aviator.predictor.ui.*
import com.aviator.predictor.ui.components.*
import com.aviator.predictor.ui.screens.*
import com.aviator.predictor.ui.theme.*
import com.aviator.predictor.utils.Formatters
import java.text.SimpleDateFormat
import java.util.*

// Navigation destinations
enum class Screen { HOME, GENERATE, RESULTS, PROFILE }

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Splash stays until auth check is done (isAuthLoading starts false now,
        // so this resolves immediately and shows the sign-in screen)
        splashScreen.setKeepOnScreenCondition {
            viewModel.state.value.isAuthLoading
        }

        setContent {
            AviatorPredictorTheme {
                val state   by viewModel.state.collectAsStateWithLifecycle()
                // Capture the Activity here so Composables can pass it to signIn()
                val activity = LocalContext.current as ComponentActivity

                AviatorApp(
                    state          = state,
                    onSignIn       = { viewModel.signIn(activity) },
                    onSignOut      = { viewModel.signOut() },
                    onGenerate     = { input, cb -> viewModel.generateSignal(input, cb) },
                    onMarkWin      = { id -> viewModel.updateSignalStatus(id, SignalStatus.WIN) },
                    onMarkLoss     = { id -> viewModel.updateSignalStatus(id, SignalStatus.LOSS) },
                    onReset        = { id -> viewModel.updateSignalStatus(id, SignalStatus.PENDING) },
                    onDelete       = { id -> viewModel.deleteSignal(id) },
                    onSaveProfile  = { profile -> viewModel.saveProfile(profile) },
                    onSaveSettings = { settings -> viewModel.saveSettings(settings) },
                    onRetrySync    = { viewModel.retrySync() },
                    getStats       = { viewModel.getStats() }
                )
            }
        }
    }
}

@Composable
fun AviatorApp(
    state: AppUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onGenerate: (String, (Boolean, String) -> Unit) -> Unit,
    onMarkWin: (String) -> Unit,
    onMarkLoss: (String) -> Unit,
    onReset: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSaveProfile: (com.aviator.predictor.data.models.UserProfile) -> Unit,
    onSaveSettings: (com.aviator.predictor.data.models.AppSettings) -> Unit,
    onRetrySync: () -> Unit,
    getStats: () -> com.aviator.predictor.data.models.Stats
) {
    // Auth loading
    if (state.isAuthLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SlateBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BrandPurple)
        }
        return
    }

    // Not signed in
    if (!state.isSignedIn) {
        SignInScreen(
            isLoading = state.isAuthLoading,
            error     = state.error,
            onSignIn  = onSignIn
        )
        return
    }

    // Loading data
    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(SlateBackground, Color(0xFF3B0764), SlateBackground))
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = BrandPurple)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading your signals…", color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
        }
        return
    }

    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    // Live clock
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    // Win/Loss bar — show when a signal is in its active/grace window
    val showWinLossBar by remember(state.upcomingSignals) {
        derivedStateOf {
            state.upcomingSignals.any { sw ->
                sw.signal.status == SignalStatus.PENDING &&
                sw.countdown <= 45 && sw.countdown >= -55
            }
        }
    }
    val winLossSignal = state.upcomingSignals.firstOrNull { sw ->
        sw.signal.status == SignalStatus.PENDING &&
        sw.countdown <= 45 && sw.countdown >= -55
    }

    Scaffold(
        containerColor = SlateBackground,
        topBar = {
            AppTopBar(
                state       = state,
                currentTime = currentTime,
                onRetrySync = onRetrySync
            )
        },
        bottomBar = {
            AppBottomBar(
                currentScreen    = currentScreen,
                onScreenChange   = { currentScreen = it },
                showWinLossBar   = showWinLossBar && currentScreen != Screen.GENERATE,
                winLossSignalId  = winLossSignal?.signal?.id,
                nextSignal       = state.upcomingSignals.firstOrNull(),
                syncStatus       = state.syncStatus,
                onMarkWin        = onMarkWin,
                onMarkLoss       = onMarkLoss
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    state             = state,
                    onMarkWin         = onMarkWin,
                    onMarkLoss        = onMarkLoss,
                    onNavigateGenerate = { currentScreen = Screen.GENERATE }
                )
                Screen.GENERATE -> GenerateScreen(
                    isGenerating = state.isGenerating,
                    onGenerate   = onGenerate
                )
                Screen.RESULTS -> ResultsScreen(
                    signals    = state.signals,
                    onMarkWin  = onMarkWin,
                    onMarkLoss = onMarkLoss,
                    onReset    = onReset,
                    onDelete   = onDelete
                )
                Screen.PROFILE -> ProfileScreen(
                    state          = state,
                    onSaveProfile  = onSaveProfile,
                    onSaveSettings = onSaveSettings,
                    onSignOut      = onSignOut
                )
            }
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    state: AppUiState,
    currentTime: Long,
    onRetrySync: () -> Unit
) {
    val timeStr = remember(currentTime) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(currentTime))
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SlateSurface.copy(alpha = 0.95f)
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(BrandPurple, BrandPink))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Aviator 1xBet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("10x Signal Generator", color = Color(0xFF94A3B8), fontSize = 10.sp)
                }
            }
        },
        actions = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33334155))
                    .border(1.dp, Color(0x33A855F7), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    timeStr,
                    color = GreenActive,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            UserAvatar(
                photoUrl    = state.user?.photoUrl,
                displayName = state.user?.displayName ?: "",
                size        = 36.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
    )
}

// ── Bottom bar ────────────────────────────────────────────────────────────────

data class NavItem(val screen: Screen, val icon: ImageVector, val label: String)

@Composable
fun AppBottomBar(
    currentScreen: Screen,
    onScreenChange: (Screen) -> Unit,
    showWinLossBar: Boolean,
    winLossSignalId: String?,
    nextSignal: com.aviator.predictor.utils.SignalWithWindow?,
    syncStatus: SyncStatus,
    onMarkWin: (String) -> Unit,
    onMarkLoss: (String) -> Unit
) {
    val navItems = listOf(
        NavItem(Screen.HOME,     Icons.Filled.Home,     "Home"),
        NavItem(Screen.GENERATE, Icons.Filled.PlayArrow, "Generate"),
        NavItem(Screen.RESULTS,  Icons.Filled.BarChart,  "Results"),
        NavItem(Screen.PROFILE,  Icons.Filled.Person,    "Profile")
    )

    Surface(
        color         = SlateSurface.copy(alpha = 0.97f),
        tonalElevation = 8.dp
    ) {
        Column {
            // Win/Loss quick bar
            AnimatedVisibility(
                visible = showWinLossBar && winLossSignalId != null,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33334155))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick  = { winLossSignalId?.let { onMarkWin(it) } },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = GreenActive.copy(alpha = 0.25f)),
                        border   = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = GreenActive, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WIN", color = GreenActive, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Button(
                        onClick  = { winLossSignalId?.let { onMarkLoss(it) } },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = RedClosed.copy(alpha = 0.25f))
                    ) {
                        Icon(Icons.Filled.Cancel, null, tint = RedClosed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LOSS", color = RedClosed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // Next signal + sync strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x22334155))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (nextSignal != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Next:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            Formatters.formatTime(nextSignal.signal.resultTime),
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Text(
                            "(${String.format("%.2f", nextSignal.signal.odd)}x)",
                            color = Color(0xFF64748B), fontSize = 11.sp
                        )
                        val cdColor = if (nextSignal.countdown <= 0) GreenActive else GreenActive
                        Text(
                            Formatters.formatCountdown(nextSignal.countdown),
                            color = cdColor, fontWeight = FontWeight.Bold, fontSize = 12.sp
                        )
                    }
                } else {
                    Text("No upcoming signals", color = Color(0xFF475569), fontSize = 11.sp)
                }
                SyncStatusPill(syncStatus)
            }

            // Tab row
            NavigationBar(
                containerColor = Color.Transparent,
                contentColor   = Color.White,
                tonalElevation = 0.dp,
                modifier       = Modifier.height(64.dp)
            ) {
                navItems.forEach { item ->
                    val selected = currentScreen == item.screen
                    NavigationBarItem(
                        selected = selected,
                        onClick  = { onScreenChange(item.screen) },
                        icon     = {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(if (selected) 26.dp else 24.dp)
                            )
                        },
                        label = {
                            Text(
                                item.label,
                                fontSize   = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = BrandPurple,
                            selectedTextColor   = BrandPurple,
                            indicatorColor      = BrandPurple.copy(alpha = 0.15f),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                }
            }
        }
    }
}
