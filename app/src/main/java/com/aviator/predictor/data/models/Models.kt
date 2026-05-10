package com.aviator.predictor.data.models

import com.google.gson.annotations.SerializedName

// ── Signal ──────────────────────────────────────────────────────────────────

data class TimeObj(
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0
)

data class WindowTimes(
    val openTime: TimeObj = TimeObj(),
    val resultTime: TimeObj = TimeObj(),
    val closeTime: TimeObj = TimeObj()
)

enum class SignalStatus { PENDING, WIN, LOSS, MISSED }

data class Signal(
    val id: String = "",
    val odd: Double = 0.0,
    val originalTime: TimeObj = TimeObj(),
    val resultTime: TimeObj = TimeObj(),
    val resultDate: String = "",
    val daysOffset: Int = 0,
    val status: SignalStatus = SignalStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val windowTimes: WindowTimes? = null,
    val autoMarked: Boolean = false,
    val missedAt: Long? = null
)

// Bet window state
enum class BetWindowStatus { WAITING, ACTIVE, GRACE, CLOSED }

data class BetWindow(
    val status: BetWindowStatus,
    val countdown: Int,           // seconds until result (negative = past)
    val windowOpensIn: Int = 0,   // seconds until window opens (WAITING only)
    val timeUntilResult: Int = 0, // seconds until result (ACTIVE only)
    val timeSinceResult: Int = 0, // seconds since result (GRACE only)
    val windowRemainingTime: Int = 0,
    val canBet: Boolean = false
)

// ── User ────────────────────────────────────────────────────────────────────

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val signalCount: Int = 0,
    val totalWins: Int = 0,
    val totalLosses: Int = 0
)

// ── Settings ─────────────────────────────────────────────────────────────────

data class AppSettings(
    val notifications: Boolean = true,
    val sound: Boolean = true,
    val autoMarkMissed: Boolean = true,
    val showCountdown: Boolean = true
)

// ── Drive storage envelope ────────────────────────────────────────────────────

data class DriveStorage(
    val signals: List<Signal> = emptyList(),
    val profile: UserProfile = UserProfile(),
    val settings: AppSettings = AppSettings(),
    val lastSynced: Long = System.currentTimeMillis()
)

// ── Stats ────────────────────────────────────────────────────────────────────

data class Stats(
    val total: Int = 0,
    val today: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val missed: Int = 0,
    val pending: Int = 0,
    val winRate: Int = 0,
    val completed: Int = 0
)
