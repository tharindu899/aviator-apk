package com.aviator.predictor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aviator.predictor.data.models.*
import com.aviator.predictor.data.repository.AuthRepository
import com.aviator.predictor.data.repository.DriveRepository
import com.aviator.predictor.utils.TimeCalculations
import com.aviator.predictor.utils.SignalWithWindow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

// ── UI State ─────────────────────────────────────────────────────────────────

enum class SyncStatus { PENDING, SYNCING, SYNCED, OFFLINE, ERROR }

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
    val currentSignal: SignalWithWindow? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = AuthRepository(application)
    private val driveRepo = DriveRepository(application)

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var autoMarkJob: Job? = null

    init {
        startAutoMarkMissed()
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    fun signIn() {
        viewModelScope.launch {
            _state.update { it.copy(isAuthLoading = true, error = null) }
            val result = authRepo.signIn()
            if (result.success) {
                _state.update {
                    it.copy(
                        isSignedIn = true,
                        user = result.profile,
                        accessToken = result.token,
                        isAuthLoading = false
                    )
                }
                loadAllData(result.token)
                startPeriodicRefresh()
            } else {
                _state.update { it.copy(isAuthLoading = false, error = result.error) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepo.signOut()
            driveRepo.clearCache()
            refreshJob?.cancel()
            _state.value = AppUiState(isAuthLoading = false)
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────

    private suspend fun loadAllData(token: String) {
        _state.update { it.copy(isLoading = true, syncStatus = SyncStatus.SYNCING) }
        try {
            val signals = driveRepo.loadSignals(token)
            val settings = driveRepo.loadSettings(token)
            val profile = driveRepo.loadProfile(token)

            val computed = computeUpcoming(signals)
            _state.update {
                it.copy(
                    signals = signals,
                    settings = settings,
                    user = profile ?: it.user,
                    isLoading = false,
                    syncStatus = SyncStatus.SYNCED,
                    upcomingSignals = computed.first,
                    currentSignal = computed.second
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    syncStatus = SyncStatus.ERROR,
                    error = e.message
                )
            }
        }
    }

    fun retrySync() {
        val token = _state.value.accessToken
        if (token.isBlank()) return
        viewModelScope.launch { loadAllData(token) }
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                val token = _state.value.accessToken
                if (token.isNotBlank() && _state.value.syncStatus == SyncStatus.SYNCED) {
                    try {
                        val signals = driveRepo.loadSignals(token)
                        val computed = computeUpcoming(signals)
                        _state.update {
                            it.copy(
                                signals = signals,
                                upcomingSignals = computed.first,
                                currentSignal = computed.second
                            )
                        }
                    } catch (_: Exception) {}
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

            val addTime = TimeCalculations.oddToTime(parsed.odd)
            val originalSecs = parsed.hour * 3600 + parsed.minute * 60 + parsed.second
            val addSecs = addTime.hours * 3600 + addTime.minutes * 60 + addTime.seconds
            var totalSecs = originalSecs + addSecs
            val daysOffset = totalSecs / 86400
            totalSecs %= 86400

            val resultTime = TimeObj(
                hours = totalSecs / 3600,
                minutes = (totalSecs % 3600) / 60,
                seconds = totalSecs % 60
            )

            val resultDate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysOffset)
            }.let {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(it.time)
            }

            // Duplicate check
            val exists = _state.value.signals.any { sig ->
                sig.status == SignalStatus.PENDING &&
                sig.resultTime == resultTime
            }
            if (exists) {
                _state.update { it.copy(isGenerating = false) }
                onResult(false, "This signal already exists!")
                return@launch
            }

            val windowTimes = TimeCalculations.getBetWindowTimes(resultTime)
            val newSignal = Signal(
                id = driveRepo.newSignalId(),
                odd = parsed.odd,
                originalTime = TimeObj(parsed.hour, parsed.minute, parsed.second),
                resultTime = resultTime,
                resultDate = resultDate,
                daysOffset = daysOffset,
                status = SignalStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                windowTimes = windowTimes
            )

            val token = _state.value.accessToken
            val ok = driveRepo.addSignal(token, newSignal)

            if (ok) {
                val newSignals = listOf(newSignal) + _state.value.signals
                val computed = computeUpcoming(newSignals)
                _state.update {
                    it.copy(
                        signals = newSignals,
                        isGenerating = false,
                        upcomingSignals = computed.first,
                        currentSignal = computed.second
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
            val token = _state.value.accessToken
            val updated = _state.value.signals.map {
                if (it.id == signalId) it.copy(status = status, updatedAt = System.currentTimeMillis())
                else it
            }
            val computed = computeUpcoming(updated)
            _state.update {
                it.copy(
                    signals = updated,
                    upcomingSignals = computed.first,
                    currentSignal = computed.second
                )
            }
            driveRepo.updateSignalStatus(token, signalId, status)
        }
    }

    fun deleteSignal(signalId: String) {
        viewModelScope.launch {
            val token = _state.value.accessToken
            val updated = _state.value.signals.filter { it.id != signalId }
            val computed = computeUpcoming(updated)
            _state.update {
                it.copy(
                    signals = updated,
                    upcomingSignals = computed.first,
                    currentSignal = computed.second
                )
            }
            driveRepo.deleteSignal(token, signalId)
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            _state.update { it.copy(settings = settings) }
            val token = _state.value.accessToken
            if (token.isNotBlank()) driveRepo.saveSettings(token, settings)
        }
    }

    // ── Profile ───────────────────────────────────────────────────────────

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            _state.update { it.copy(user = profile) }
            val token = _state.value.accessToken
            if (token.isNotBlank()) driveRepo.saveProfile(token, profile)
        }
    }

    // ── Auto-mark missed ──────────────────────────────────────────────────

    private fun startAutoMarkMissed() {
        autoMarkJob?.cancel()
        autoMarkJob = viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                val current = _state.value
                if (!current.isSignedIn || current.accessToken.isBlank()) continue

                val toMark = current.signals.filter {
                    TimeCalculations.shouldMarkAsMissed(it)
                }
                if (toMark.isNotEmpty()) {
                    val updates = toMark.associate { it.id to SignalStatus.MISSED }
                    val updated = current.signals.map { sig ->
                        if (updates.containsKey(sig.id))
                            sig.copy(status = SignalStatus.MISSED, updatedAt = System.currentTimeMillis(), autoMarked = true)
                        else sig
                    }
                    val computed = computeUpcoming(updated)
                    _state.update {
                        it.copy(
                            signals = updated,
                            upcomingSignals = computed.first,
                            currentSignal = computed.second
                        )
                    }
                    driveRepo.batchUpdateStatus(current.accessToken, updates)
                }

                // Also recompute upcoming every tick to keep countdowns live
                val recomputed = computeUpcoming(current.signals)
                _state.update {
                    it.copy(
                        upcomingSignals = recomputed.first,
                        currentSignal = recomputed.second
                    )
                }
            }
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    fun getStats(): Stats {
        val signals = _state.value.signals
        val total = signals.size
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = signals.count { it.createdAt >= todayStart }
        val wins = signals.count { it.status == SignalStatus.WIN }
        val losses = signals.count { it.status == SignalStatus.LOSS }
        val missed = signals.count { it.status == SignalStatus.MISSED }
        val pending = signals.count { it.status == SignalStatus.PENDING }
        val completed = wins + losses
        val winRate = if (completed > 0) (wins * 100) / completed else 0
        return Stats(total, today, wins, losses, missed, pending, winRate, completed)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun computeUpcoming(signals: List<Signal>): Pair<List<SignalWithWindow>, SignalWithWindow?> {
        val upcoming = TimeCalculations.getUpcomingSignals(signals)
        val current = TimeCalculations.getCurrentSignal(upcoming)
        return Pair(upcoming, current)
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
