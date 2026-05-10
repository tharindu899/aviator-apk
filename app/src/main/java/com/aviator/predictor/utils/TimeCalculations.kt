package com.aviator.predictor.utils

import com.aviator.predictor.data.models.*
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

object TimeCalculations {

    // Convert odd to time offset — same algorithm as the JS web app
    fun oddToTime(odd: Double): TimeObj {
        var minutes = Math.ceil(odd).toInt()
        val decimalPart = odd - odd.toLong()
        var seconds = (decimalPart * 100 - 60).roundToInt()

        if (seconds < 0) {
            minutes -= 1
            seconds = 60 + seconds
        }

        var hours = 0
        if (minutes >= 60) {
            hours = minutes / 60
            minutes %= 60
        }

        hours %= 24
        return TimeObj(hours, minutes, seconds)
    }

    // Parse input like "2.02x 21:31:22" or "150x 23:34:00"
    fun parseInput(input: String): ParsedInput? {
        val cleaned = input.trim().replace("\\s+".toRegex(), " ").replace(",", "")
        val pattern = Regex("""([0-9.]+)x?\s*(\d{1,2}):(\d{1,2}):(\d{1,2})""", RegexOption.IGNORE_CASE)
        val match = pattern.find(cleaned) ?: return null

        val odd = match.groupValues[1].toDoubleOrNull() ?: return null
        val hour = match.groupValues[2].toIntOrNull() ?: return null
        val minute = match.groupValues[3].toIntOrNull() ?: return null
        val second = match.groupValues[4].toIntOrNull() ?: return null

        if (odd <= 0 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
        return ParsedInput(odd, hour, minute, second)
    }

    // Calculate countdown in seconds to the result time
    fun getCountdown(signal: Signal): Int {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()

        if (signal.resultDate.isNotEmpty()) {
            // Parse ISO date string
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(signal.resultDate) ?: return 999999
                target.time = date
                target.set(Calendar.HOUR_OF_DAY, signal.resultTime.hours)
                target.set(Calendar.MINUTE, signal.resultTime.minutes)
                target.set(Calendar.SECOND, signal.resultTime.seconds)
                target.set(Calendar.MILLISECOND, 0)
            } catch (e: Exception) {
                setTargetTime(target, signal.resultTime, signal.daysOffset)
            }
        } else {
            setTargetTime(target, signal.resultTime, signal.daysOffset)
        }

        return ((target.timeInMillis - now.timeInMillis) / 1000).toInt()
    }

    private fun setTargetTime(cal: Calendar, time: TimeObj, daysOffset: Int) {
        cal.set(Calendar.HOUR_OF_DAY, time.hours)
        cal.set(Calendar.MINUTE, time.minutes)
        cal.set(Calendar.SECOND, time.seconds)
        cal.set(Calendar.MILLISECOND, 0)
        if (daysOffset > 0) cal.add(Calendar.DAY_OF_YEAR, daysOffset)
    }

    // Get bet window status from countdown value
    fun getBetWindowStatus(countdown: Int): BetWindow {
        return when {
            countdown > 45 -> BetWindow(
                status = BetWindowStatus.WAITING,
                countdown = countdown,
                windowOpensIn = countdown - 45,
                canBet = false
            )
            countdown in 0..45 -> BetWindow(
                status = BetWindowStatus.ACTIVE,
                countdown = countdown,
                timeUntilResult = countdown,
                windowRemainingTime = countdown + 45,
                canBet = true
            )
            countdown < 0 && countdown >= -45 -> BetWindow(
                status = BetWindowStatus.GRACE,
                countdown = countdown,
                timeSinceResult = abs(countdown),
                windowRemainingTime = 45 + countdown,
                canBet = true
            )
            else -> BetWindow(
                status = BetWindowStatus.CLOSED,
                countdown = countdown,
                canBet = false
            )
        }
    }

    // Calculate the 45s before/after window times
    fun getBetWindowTimes(resultTime: TimeObj): WindowTimes {
        val resultSecs = resultTime.hours * 3600 + resultTime.minutes * 60 + resultTime.seconds

        var openSecs = resultSecs - 45
        if (openSecs < 0) openSecs += 86400
        val openTime = TimeObj(
            hours = (openSecs / 3600) % 24,
            minutes = (openSecs % 3600) / 60,
            seconds = openSecs % 60
        )

        val closeSecs = resultSecs + 45
        val closeTime = TimeObj(
            hours = (closeSecs / 3600) % 24,
            minutes = (closeSecs % 3600) / 60,
            seconds = closeSecs % 60
        )

        return WindowTimes(openTime, resultTime, closeTime)
    }

    // Should this signal be auto-marked as missed?
    fun shouldMarkAsMissed(signal: Signal): Boolean {
        if (signal.status != SignalStatus.PENDING) return false
        return getCountdown(signal) < -55
    }

    // Get upcoming signals sorted by countdown, excluding stale ones
    fun getUpcomingSignals(signals: List<Signal>): List<SignalWithWindow> {
        val seenTimes = mutableMapOf<String, SignalWithWindow>()

        signals
            .filter { it.status == SignalStatus.PENDING && it.resultTime != TimeObj() }
            .map { signal ->
                val countdown = getCountdown(signal)
                val betWindow = getBetWindowStatus(countdown)
                val windowTimes = signal.windowTimes ?: getBetWindowTimes(signal.resultTime)
                SignalWithWindow(signal, countdown, betWindow, windowTimes)
            }
            .filter { it.countdown >= -55 && it.countdown < 7 * 24 * 3600 }
            .sortedBy { it.countdown }
            .forEach { sw ->
                val datePrefix = sw.signal.resultDate.take(10).ifEmpty {
                    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                }
                val timeKey = "$datePrefix-${sw.signal.resultTime.hours.toString().padStart(2,'0')}:${sw.signal.resultTime.minutes.toString().padStart(2,'0')}:${sw.signal.resultTime.seconds.toString().padStart(2,'0')}"

                if (!seenTimes.containsKey(timeKey)) {
                    seenTimes[timeKey] = sw
                } else {
                    val existing = seenTimes[timeKey]!!
                    if (sw.signal.createdAt < existing.signal.createdAt) {
                        seenTimes[timeKey] = sw
                    }
                }
            }

        return seenTimes.values.sortedBy { it.countdown }
    }

    fun getCurrentSignal(signals: List<SignalWithWindow>): SignalWithWindow? {
        if (signals.isEmpty()) return null
        return signals.firstOrNull {
            it.betWindow.status == BetWindowStatus.ACTIVE || it.betWindow.status == BetWindowStatus.GRACE
        } ?: signals.firstOrNull()
    }
}

data class ParsedInput(val odd: Double, val hour: Int, val minute: Int, val second: Int)

data class SignalWithWindow(
    val signal: Signal,
    val countdown: Int,
    val betWindow: BetWindow,
    val windowTimes: WindowTimes
)
