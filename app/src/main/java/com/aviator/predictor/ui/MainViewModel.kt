package com.aviator.predictor.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aviator.predictor.data.models.*
import com.aviator.predictor.data.repository.AuthRepository
import com.aviator.predictor.data.repository.DriveRepository
import com.aviator.predictor.utils.NotificationHelper
import com.aviator.predictor.utils.PendingSignalAction
import com.aviator.predictor.utils.SignalWithWindow
import com.aviator.predictor.utils.TimeCalculations
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

// ── UI State ──────────────────────────────────────────────────────────────────

enum class SyncStatus { PENDING, SYNCING, SYNCED, OFFLINE, ERROR }

data class AppUiState(
    val isAuthLoading: Boolean = true,    // true on launch while session restores
    val isSignedIn: Boolean = false,
    val user: UserProfile? = null,
    val accessToken: String = "",
    val signals: List<Signal> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val error: String? = null,
    val upcomingSignals: List<SignalWithWindow> = emptyList(),
    val currentSignal: SignalWithWindow? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo  = AuthRepository(application)
    private val driveRepo = DriveRepository(application)

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null   // 1 s – local countdown refresh (no Drive)
    private var syncJob: Job? = null        // 30 s – Drive pull
    private var autoMarkJob: Job? = null    // 3 s – missed detection + notifications

    private val notifiedSignalIds = mutableSetOf<String>()

    init {
        NotificationHelper.createChannel(application)
        startAutoMarkMissed()
        collectPendingActions()
        // Try to restore the previous session silently.
        // isAuthLoading = true keeps the splash screen on until we know the answer.
        tryRestoreSession()
    }

    // ── Silent session restore (runs every cold start) ────────────────────

    private fun tryRestoreSession() {
        viewModelScope.launch {
            val result = authRepo.tryRestoreSession()
            if (result.success && result.profile != null) {
                _state.update {
                    it.copy(
                        isSignedIn    = true,
                        user          = result.profile,
                        accessToken   = result.token,
                        isAuthLoading = false
                    )
                }
                loadAllData(result.token)
                startCountdownUpdater()
                startPeriodicSync()
            } else {
                // No saved session — show sign-in screen
                _state.update { it.copy(isAuthLoading = false) }
            }
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _state.update { it.copy(isAuthLoading = true, error = null) }
            val result = authRepo.signIn(activity)
            if (result.success && result.profile != null) {
                _state.update {
                    it.copy(
                        isSignedIn    = true,
                        user          = result.profile,
                        accessToken   = result.token,
                        isAuthLoading = false
                    )
                }
                loadAllData(result.token)
                startCountdownUpdater()
                startPeriodicSync()
            } else {
                _state.update { it.copy(isAuthLoading = false, error = result.error) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepo.signOut()          // clears DataStore + credential state
            driveRepo.clearCache()
            countdownJob?.cancel()
            syncJob?.cancel()
            NotificationHelper.cancelAll(getApplication())
            notifiedSignalIds.clear()
            _state.value = AppUiState(isAuthLoading = false)   // show sign-in immediately
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────

    private suspend fun loadAllData(token: String) {
        _state.update { it.copy(isLoading = true, syncStatus = SyncStatus.SYNCING) }
        try {
            val signals  = driveRepo.loadSignals(token)
            val settings = driveRepo.loadSettings(token)
            val profile  = driveRepo.loadProfile(token)
            val computed = computeUpcoming(signals)
            _state.update {
                it.copy(
                    signals         = signals,
                    settings        = settings,
                    user            = profile ?: it.user,
                    isLoading       = false,
                    syncStatus      = SyncStatus.SYNCED,
                    upcomingSignals = computed.first,
                    currentSignal   = computed.second
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading  = false,
                    syncStatus = SyncStatus.ERROR,
                    error      = "Sync failed: ${e.message}"
                )
            }
        }
    }

    fun retrySync() {
        val token = _state.value.accessToken
        if (token.isBlank()) return
        viewModelScope.launch { loadAllData(token) }
    }

    // ── 1-second countdown updater (pure local, no Drive calls) ──────────

    private fun startCountdownUpdater() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val current = _state.value
                if (!current.isSignedIn || current.signals.isEmpty()) continue
                val computed = computeUpcoming(current.signals)
                _state.update {
                    it.copy(
                        upcomingSignals = computed.first,
                        currentSignal   = computed.second
                    )
                }
            }
        }
    }

    // ── 30-second Drive sync ──────────────────────────────────────────────

    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                val token = _state.value.accessToken
                if (token.isBlank() || !_state.value.isSignedIn) continue
                try {
                    val signals  = driveRepo.loadSignals(token)
                    val computed = computeUpcoming(signals)
                    _state.update {
                        it.copy(
                            signals         = signals,
                            syncStatus      = SyncStatus.SYNCED,
                            upcomingSignals = computed.first,
                            currentSignal   = computed.second
                        )
                    }
                } catch (_: Exception) {
                    _state.update { it.copy(syncStatus = SyncStatus.OFFLINE) }
                }
            }
        }
    }

    // ── Notification action collection ────────────────────────────────────

    private fun collectPendingActions() {
        viewModelScope.launch {
            PendingSignalAction.flow.collect { (signalId, status) ->
                updateSignalStatus(signalId, status)
            }
        }
    }

    // ── Notification management ───────────────────────────────────────────

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun syncNotifications(signals: List<Signal>) {
        if (!hasNotificationPermission()) return
        val context = getApplication<Application>()

        signals.forEach { signal ->
            if (signal.status != SignalStatus.PENDING) {
                if (notifiedSignalIds.remove(signal.id)) {
                    NotificationHelper.cancelNotification(context, signal.id)
                }
                return@forEach
            }

            val countdown    = TimeCalculations.getCountdown(signal)
            val inWindow     = countdown <= 45 && countdown >= -45
            val windowClosed = countdown < -45

            when {
                inWindow -> {
                    val isFirstShow = !notifiedSignalIds.contains(signal.id)
                    val betWindow   = TimeCalculations.getBetWindowStatus(countdown)
                    NotificationHelper.showActiveNotification(
                        context      = context,
                        signal       = signal,
                        windowStatus = betWindow.status,
                        countdown    = countdown,
                        playSound    = isFirstShow
                    )
                    notifiedSignalIds.add(signal.id)
                }
                windowClosed && notifiedSignalIds.remove(signal.id) -> {
                    NotificationHelper.cancelNotification(context, signal.id)
                }
            }
        }
    }

    // ── Signal generation ─────────────────────────────────────────────────

    fun generateSignal(input: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val parsed = TimeCalculations.parseInput(input)
            if (parsed == null) {
                onResult(false, "Invalid format! Use: 2.02x 21:31:22")
                return@launch
            }

            _state.update { it.copy(isGenerating = true) }

            val addTime      = TimeCalculations.oddToTime(parsed.odd)
            val originalSecs = parsed.hour * 3600 + parsed.minute * 60 + parsed.second
            val addSecs      = addTime.hours * 3600 + addTime.minutes * 60 + addTime.seconds
            var totalSecs    = originalSecs + addSecs
            val daysOffset   = totalSecs / 86400
            totalSecs       %= 86400

            val resultTime = TimeObj(
                hours   = totalSecs / 3600,
                minutes = (totalSecs % 3600) / 60,
                seconds = totalSecs % 60
            )

            val resultDate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysOffset)
            }.let {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(it.time)
            }

            val exists = _state.value.signals.any { sig ->
                sig.status == SignalStatus.PENDING && sig.resultTime == resultTime
            }
            if (exists) {
                _state.update { it.copy(isGenerating = false) }
                onResult(false, "This signal already exists!")
                return@launch
            }

            val windowTimes = TimeCalculations.getBetWindowTimes(resultTime)
            val newSignal = Signal(
                id           = driveRepo.newSignalId(),
                odd          = parsed.odd,
                originalTime = TimeObj(parsed.hour, parsed.minute, parsed.second),
                resultTime   = resultTime,
                resultDate   = resultDate,
                daysOffset   = daysOffset,
                status       = SignalStatus.PENDING,
                createdAt    = System.currentTimeMillis(),
                updatedAt    = System.currentTimeMillis(),
                windowTimes  = windowTimes
            )

            val token = _state.value.accessToken
            val ok    = driveRepo.addSignal(token, newSignal)

            if (ok) {
                val newSignals = listOf(newSignal) + _state.value.signals
                val computed   = computeUpcoming(newSignals)
                _state.update {
                    it.copy(
                        signals         = newSignals,
                        isGenerating    = false,
                        upcomingSignals = computed.first,
                        currentSignal   = computed.second
                    )
                }
                onResult(true, "Signal generated!")
            } else {
                _state.update { it.copy(isGenerating = false) }
                onResult(false, "Failed to save signal. Check connection.")
            }
        }
    }

    // ── Signal actions ────────────────────────────────────────────────────

    fun updateSignalStatus(signalId: String, status: SignalStatus) {
        viewModelScope.launch {
            val token   = _state.value.accessToken
            val updated = _state.value.signals.map {
                if (it.id == signalId)
                    it.copy(status = status, updatedAt = System.currentTimeMillis())
                else it
            }
            val computed = computeUpcoming(updated)
            _state.update {
                it.copy(
                    signals         = updated,
                    upcomingSignals = computed.first,
                    currentSignal   = computed.second
                )
            }
            if (notifiedSignalIds.remove(signalId)) {
                NotificationHelper.cancelNotification(getApplication(), signalId)
            }
            if (token.isNotBlank()) {
                launch { runCatching { driveRepo.updateSignalStatus(token, signalId, status) } }
            }
        }
    }

    fun deleteSignal(signalId: String) {
        viewModelScope.launch {
            val token    = _state.value.accessToken
            val updated  = _state.value.signals.filter { it.id != signalId }
            val computed = computeUpcoming(updated)
            _state.update {
                it.copy(
                    signals         = updated,
                    upcomingSignals = computed.first,
                    currentSignal   = computed.second
                )
            }
            if (notifiedSignalIds.remove(signalId)) {
                NotificationHelper.cancelNotification(getApplication(), signalId)
            }
            if (token.isNotBlank()) {
                launch { runCatching { driveRepo.deleteSignal(token, signalId) } }
            }
        }
    }

    // ── Settings / Profile ────────────────────────────────────────────────

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            _state.update { it.copy(settings = settings) }
            val token = _state.value.accessToken
            if (token.isNotBlank()) {
                launch { runCatching { driveRepo.saveSettings(token, settings) } }
            }
        }
    }

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            _state.update { it.copy(user = profile) }
            // Keep DataStore name/photo in sync so restore shows correct info
            launch { runCatching { authRepo.saveSession(profile) } }
            val token = _state.value.accessToken
            if (token.isNotBlank()) {
                launch { runCatching { driveRepo.saveProfile(token, profile) } }
            }
        }
    }

    // ── Auto-mark missed + notification sync (runs every 3 s) ────────────

    private fun startAutoMarkMissed() {
        autoMarkJob?.cancel()
        autoMarkJob = viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                val current = _state.value
                if (!current.isSignedIn || current.accessToken.isBlank()) continue

                syncNotifications(current.signals)

                val toMark = current.signals.filter { TimeCalculations.shouldMarkAsMissed(it) }
                if (toMark.isEmpty()) continue

                val updates = toMark.associate { it.id to SignalStatus.MISSED }
                val updated = current.signals.map { sig ->
                    if (updates.containsKey(sig.id))
                        sig.copy(
                            status     = SignalStatus.MISSED,
                            updatedAt  = System.currentTimeMillis(),
                            autoMarked = true
                        )
                    else sig
                }
                val computed = computeUpcoming(updated)
                _state.update {
                    it.copy(
                        signals         = updated,
                        upcomingSignals = computed.first,
                        currentSignal   = computed.second
                    )
                }
                toMark.forEach { sig ->
                    if (notifiedSignalIds.remove(sig.id)) {
                        NotificationHelper.cancelNotification(getApplication(), sig.id)
                    }
                }
                launch {
                    runCatching { driveRepo.batchUpdateStatus(current.accessToken, updates) }
                }
            }
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    fun getStats(): Stats {
        val signals    = _state.value.signals
        val total      = signals.size
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today     = signals.count { it.createdAt >= todayStart }
        val wins      = signals.count { it.status == SignalStatus.WIN }
        val losses    = signals.count { it.status == SignalStatus.LOSS }
        val missed    = signals.count { it.status == SignalStatus.MISSED }
        val pending   = signals.count { it.status == SignalStatus.PENDING }
        val completed = wins + losses
        val winRate   = if (completed > 0) (wins * 100) / completed else 0
        return Stats(total, today, wins, losses, missed, pending, winRate, completed)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun computeUpcoming(signals: List<Signal>): Pair<List<SignalWithWindow>, SignalWithWindow?> {
        val upcoming = TimeCalculations.getUpcomingSignals(signals)
        val current  = TimeCalculations.getCurrentSignal(upcoming)
        return Pair(upcoming, current)
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
