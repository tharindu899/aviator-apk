package com.aviator.predictor.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aviator.predictor.BuildConfig
import com.aviator.predictor.data.models.*
import com.aviator.predictor.data.repository.AuthRepository
import com.aviator.predictor.data.repository.DriveRepository
import com.aviator.predictor.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.*

// ── UI State ──────────────────────────────────────────────────────────────────

enum class SyncStatus { PENDING, SYNCING, SYNCED, OFFLINE, ERROR }

sealed class UpdateDownloadState {
    object Idle                                : UpdateDownloadState()
    data class Downloading(val percent: Int)   : UpdateDownloadState()
    data class ReadyToInstall(val file: File)  : UpdateDownloadState()
    object Failed                              : UpdateDownloadState()
}

data class AppUiState(
    val isAuthLoading: Boolean = true,
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
    val currentSignal: SignalWithWindow? = null,
    // ── Update ──────────────────────────────────────────────
    val updateInfo: UpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val updateDownloadState: UpdateDownloadState = UpdateDownloadState.Idle
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo  = AuthRepository(application)
    private val driveRepo = DriveRepository(application)

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private var syncJob: Job? = null
    private var autoMarkJob: Job? = null
    private var downloadJob: Job? = null
    private var activeDownloadId: Long = -1L

    private val notifiedSignalIds = mutableSetOf<String>()

    init {
        NotificationHelper.createChannel(application)
        startAutoMarkMissed()
        collectPendingActions()
        tryRestoreSession()
    }

    // ── Session restore ───────────────────────────────────────────────────

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
                _state.update { it.copy(isAuthLoading = false) }
            }
            // Always check for updates on launch
            checkForUpdate()
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
            authRepo.signOut()
            driveRepo.clearCache()
            countdownJob?.cancel()
            syncJob?.cancel()
            cancelDownloadIfActive()
            NotificationHelper.cancelAll(getApplication())
            notifiedSignalIds.clear()
            _state.value = AppUiState(isAuthLoading = false)
        }
    }

    // ── Update check ──────────────────────────────────────────────────────

    fun checkForUpdate() {
        viewModelScope.launch {
            val info = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME) ?: return@launch
            _state.update {
                it.copy(
                    updateInfo       = info,
                    showUpdateDialog = true
                )
            }
        }
    }

    fun showUpdateDialog()  = _state.update { it.copy(showUpdateDialog = true) }

    fun dismissUpdateDialog() {
        if (_state.value.updateDownloadState is UpdateDownloadState.Downloading) return
        _state.update { it.copy(showUpdateDialog = false) }
    }

    // ── Download ──────────────────────────────────────────────────────────

    fun startUpdateDownload() {
        val info    = _state.value.updateInfo ?: return
        val context = getApplication<Application>()

        // Reuse cached APK if already downloaded
        val existing = UpdateInstaller.findDownloadedApk(context, info.apkFileName)
        if (existing != null) {
            _state.update {
                it.copy(updateDownloadState = UpdateDownloadState.ReadyToInstall(existing))
            }
            return
        }

        // Check "install unknown apps" permission
        if (!UpdateInstaller.canInstall(context)) {
            UpdateInstaller.openInstallPermissionSettings(context)
            return
        }

        cancelDownloadIfActive()

        val downloadId = UpdateInstaller.startDownload(context, info.downloadUrl, info.apkFileName)
        if (downloadId == -1L) {
            _state.update { it.copy(updateDownloadState = UpdateDownloadState.Failed) }
            return
        }

        activeDownloadId = downloadId
        _state.update { it.copy(updateDownloadState = UpdateDownloadState.Downloading(0)) }

        downloadJob = viewModelScope.launch {
            // ✅ Pass info.apkFileName so pollProgress can resolve the file on completion
            UpdateInstaller.pollProgress(context, downloadId, info.apkFileName) { progress ->
                when {
                    progress.isFailed -> {
                        _state.update { it.copy(updateDownloadState = UpdateDownloadState.Failed) }
                    }
                    progress.isComplete && progress.localFile != null -> {
                        _state.update {
                            it.copy(
                                updateDownloadState = UpdateDownloadState.ReadyToInstall(progress.localFile)
                            )
                        }
                    }
                    else -> {
                        _state.update {
                            it.copy(updateDownloadState = UpdateDownloadState.Downloading(progress.percent))
                        }
                    }
                }
            }
        }
    }

    // ── Install ───────────────────────────────────────────────────────────

    fun installUpdate() {
        val dlState = _state.value.updateDownloadState
        val context = getApplication<Application>()
        if (dlState !is UpdateDownloadState.ReadyToInstall) return
        if (!UpdateInstaller.canInstall(context)) {
            UpdateInstaller.openInstallPermissionSettings(context)
            return
        }
        // ✅ Verify the file still exists before trying to install
        if (!dlState.file.exists()) {
            _state.update { it.copy(updateDownloadState = UpdateDownloadState.Failed) }
            return
        }
        UpdateInstaller.installApk(context, dlState.file)
    }

    private fun cancelDownloadIfActive() {
        if (activeDownloadId != -1L) {
            UpdateInstaller.cancelDownload(getApplication(), activeDownloadId)
            activeDownloadId = -1L
        }
        downloadJob?.cancel()
        downloadJob = null
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

    private fun startCountdownUpdater() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val current = _state.value
                if (!current.isSignedIn || current.signals.isEmpty()) continue
                val computed = computeUpcoming(current.signals)
                _state.update {
                    it.copy(upcomingSignals = computed.first, currentSignal = computed.second)
                }
            }
        }
    }

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
                        it.copy(signals = signals, syncStatus = SyncStatus.SYNCED,
                            upcomingSignals = computed.first, currentSignal = computed.second)
                    }
                } catch (_: Exception) {
                    _state.update { it.copy(syncStatus = SyncStatus.OFFLINE) }
                }
            }
        }
    }

    private fun collectPendingActions() {
        viewModelScope.launch {
            PendingSignalAction.flow.collect { (signalId, status) ->
                updateSignalStatus(signalId, status)
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun syncNotifications(signals: List<Signal>, settings: AppSettings) {
        if (!settings.notifications) {
            if (notifiedSignalIds.isNotEmpty()) {
                val ctx = getApplication<Application>()
                notifiedSignalIds.forEach { NotificationHelper.cancelNotification(ctx, it) }
                notifiedSignalIds.clear()
            }
            return
        }
        if (!hasNotificationPermission()) return
        val context = getApplication<Application>()
        signals.forEach { signal ->
            if (signal.status != SignalStatus.PENDING) {
                if (notifiedSignalIds.remove(signal.id))
                    NotificationHelper.cancelNotification(context, signal.id)
                return@forEach
            }
            val countdown    = TimeCalculations.getCountdown(signal)
            val inWindow     = countdown <= 45 && countdown >= -45
            val windowClosed = countdown < -45
            when {
                inWindow -> {
                    val isFirst   = !notifiedSignalIds.contains(signal.id)
                    val playSound = isFirst && settings.sound
                    val betWindow = TimeCalculations.getBetWindowStatus(countdown)
                    NotificationHelper.showActiveNotification(context, signal, betWindow.status, countdown, playSound)
                    notifiedSignalIds.add(signal.id)
                }
                windowClosed && notifiedSignalIds.remove(signal.id) ->
                    NotificationHelper.cancelNotification(context, signal.id)
            }
        }
    }

    fun generateSignal(input: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val parsed = TimeCalculations.parseInput(input)
            if (parsed == null) { onResult(false, "Invalid format! Use: 2.02x 21:31:22"); return@launch }
            _state.update { it.copy(isGenerating = true) }
            val addTime      = TimeCalculations.oddToTime(parsed.odd)
            val originalSecs = parsed.hour * 3600 + parsed.minute * 60 + parsed.second
            val addSecs      = addTime.hours * 3600 + addTime.minutes * 60 + addTime.seconds
            var totalSecs    = originalSecs + addSecs
            val daysOffset   = totalSecs / 86400
            totalSecs       %= 86400
            val resultTime   = TimeObj(totalSecs / 3600, (totalSecs % 3600) / 60, totalSecs % 60)
            val resultDate   = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysOffset) }.let {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(it.time)
            }
            if (_state.value.signals.any { it.status == SignalStatus.PENDING && it.resultTime == resultTime }) {
                _state.update { it.copy(isGenerating = false) }
                onResult(false, "This signal already exists!"); return@launch
            }
            val windowTimes = TimeCalculations.getBetWindowTimes(resultTime)
            val newSignal = Signal(
                id           = driveRepo.newSignalId(), odd = parsed.odd,
                originalTime = TimeObj(parsed.hour, parsed.minute, parsed.second),
                resultTime   = resultTime, resultDate = resultDate, daysOffset = daysOffset,
                status       = SignalStatus.PENDING,
                createdAt    = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
                windowTimes  = windowTimes
            )
            val token = _state.value.accessToken
            if (driveRepo.addSignal(token, newSignal)) {
                val newSignals = listOf(newSignal) + _state.value.signals
                val computed   = computeUpcoming(newSignals)
                _state.update {
                    it.copy(signals = newSignals, isGenerating = false,
                        upcomingSignals = computed.first, currentSignal = computed.second)
                }
                onResult(true, "Signal generated!")
            } else {
                _state.update { it.copy(isGenerating = false) }
                onResult(false, "Failed to save signal. Check connection.")
            }
        }
    }

    fun updateSignalStatus(signalId: String, status: SignalStatus) {
        viewModelScope.launch {
            val token   = _state.value.accessToken
            val updated = _state.value.signals.map {
                if (it.id == signalId) it.copy(status = status, updatedAt = System.currentTimeMillis()) else it
            }
            val computed = computeUpcoming(updated)
            _state.update {
                it.copy(signals = updated, upcomingSignals = computed.first, currentSignal = computed.second)
            }
            if (notifiedSignalIds.remove(signalId))
                NotificationHelper.cancelNotification(getApplication(), signalId)
            if (token.isNotBlank())
                launch { runCatching { driveRepo.updateSignalStatus(token, signalId, status) } }
        }
    }

    fun deleteSignal(signalId: String) {
        viewModelScope.launch {
            val token    = _state.value.accessToken
            val updated  = _state.value.signals.filter { it.id != signalId }
            val computed = computeUpcoming(updated)
            _state.update {
                it.copy(signals = updated, upcomingSignals = computed.first, currentSignal = computed.second)
            }
            if (notifiedSignalIds.remove(signalId))
                NotificationHelper.cancelNotification(getApplication(), signalId)
            if (token.isNotBlank())
                launch { runCatching { driveRepo.deleteSignal(token, signalId) } }
        }
    }

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            _state.update { it.copy(settings = settings) }
            val token = _state.value.accessToken
            if (token.isNotBlank()) launch { runCatching { driveRepo.saveSettings(token, settings) } }
        }
    }

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            _state.update { it.copy(user = profile) }
            launch { runCatching { authRepo.saveSession(profile) } }
            val token = _state.value.accessToken
            if (token.isNotBlank()) launch { runCatching { driveRepo.saveProfile(token, profile) } }
        }
    }

    private fun startAutoMarkMissed() {
        autoMarkJob?.cancel()
        autoMarkJob = viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                val current = _state.value
                if (!current.isSignedIn || current.accessToken.isBlank()) continue
                syncNotifications(current.signals, current.settings)
                if (!current.settings.autoMarkMissed) continue
                val toMark = current.signals.filter { TimeCalculations.shouldMarkAsMissed(it) }
                if (toMark.isEmpty()) continue
                val updates = toMark.associate { it.id to SignalStatus.MISSED }
                val updated = current.signals.map { sig ->
                    if (updates.containsKey(sig.id))
                        sig.copy(status = SignalStatus.MISSED, updatedAt = System.currentTimeMillis(), autoMarked = true)
                    else sig
                }
                val computed = computeUpcoming(updated)
                _state.update {
                    it.copy(signals = updated, upcomingSignals = computed.first, currentSignal = computed.second)
                }
                toMark.forEach { sig ->
                    if (notifiedSignalIds.remove(sig.id))
                        NotificationHelper.cancelNotification(getApplication(), sig.id)
                }
                launch { runCatching { driveRepo.batchUpdateStatus(current.accessToken, updates) } }
            }
        }
    }

    fun getStats(): Stats {
        val signals    = _state.value.signals
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
        return Stats(signals.size, today, wins, losses, missed, pending, winRate, completed)
    }

    private fun computeUpcoming(signals: List<Signal>): Pair<List<SignalWithWindow>, SignalWithWindow?> {
        val upcoming = TimeCalculations.getUpcomingSignals(signals)
        return Pair(upcoming, TimeCalculations.getCurrentSignal(upcoming))
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
