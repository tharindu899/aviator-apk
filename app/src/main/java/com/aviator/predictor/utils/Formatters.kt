package com.aviator.predictor.utils

import com.aviator.predictor.data.models.TimeObj
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

object Formatters {

    fun formatTime(timeObj: TimeObj): String =
        "${timeObj.hours.toString().padStart(2, '0')}:" +
        "${timeObj.minutes.toString().padStart(2, '0')}:" +
        "${timeObj.seconds.toString().padStart(2, '0')}"

    fun formatCountdown(seconds: Int): String {
        if (seconds < -30) return "Passed"
        if (seconds < 0) return "${abs(seconds)}s ago"
        if (seconds == 0) return "NOW!"

        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60

        return when {
            h > 0 -> "${h}h ${m}m ${s}s"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun getInitials(name: String): String {
        if (name.isBlank()) return "?"
        return name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it[0].uppercaseChar() }
            .joinToString("")
    }
}
