package com.aviator.predictor.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aviator.predictor.MainActivity
import com.aviator.predictor.R
import com.aviator.predictor.data.models.BetWindowStatus
import com.aviator.predictor.data.models.Signal

object NotificationHelper {

    const val CHANNEL_ID    = "aviator_signal_alerts"
    const val ACTION_WIN    = "com.aviator.predictor.ACTION_WIN"
    const val ACTION_LOSS   = "com.aviator.predictor.ACTION_LOSS"
    const val EXTRA_SIGNAL_ID = "extra_signal_id"

    // ── Channel (call once, idempotent) ───────────────────────────────────

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Signal Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Bet-window alerts with quick Win / Loss actions"
            enableVibration(true)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    // ── Show / update the active-signal notification ──────────────────────

    fun showActiveNotification(
        context: Context,
        signal: Signal,
        windowStatus: BetWindowStatus,
        countdown: Int
    ) {
        val notifId   = signal.id.hashCode()
        val oddStr    = String.format("%.2f", signal.odd)
        val timeStr   = Formatters.formatTime(signal.resultTime)

        val title = when (windowStatus) {
            BetWindowStatus.ACTIVE -> "🟢 BET WINDOW OPEN — ${oddStr}x"
            BetWindowStatus.GRACE  -> "🟡 GRACE PERIOD — ${oddStr}x"
            else                   -> return          // shouldn't happen
        }

        val body = buildString {
            append("Signal: $timeStr")
            if (countdown > 0) append(" · ${countdown}s to result")
            else if (countdown < 0) append(" · ${-countdown}s past result")
            else append(" · Result NOW")
        }

        // Tap → bring app to front
        val tapPending = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── WIN action ────────────────────────────────────────────────────
        val winPending = PendingIntent.getBroadcast(
            context, notifId + 1,
            Intent(ACTION_WIN).apply {
                `package` = context.packageName
                putExtra(EXTRA_SIGNAL_ID, signal.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── LOSS action ───────────────────────────────────────────────────
        val lossPending = PendingIntent.getBroadcast(
            context, notifId + 2,
            Intent(ACTION_LOSS).apply {
                `package` = context.packageName
                putExtra(EXTRA_SIGNAL_ID, signal.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_splash)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapPending)
            .setAutoCancel(false)
            .setOngoing(true)           // stays visible until marked or expired
            .setSilent(countdown < 0)   // no sound during grace period
            .addAction(
                android.R.drawable.checkbox_on_background,
                "✓  WIN",
                winPending
            )
            .addAction(
                android.R.drawable.ic_delete,
                "✗  LOSS",
                lossPending
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted — silently skip
        }
    }

    // ── Dismiss helpers ───────────────────────────────────────────────────

    fun cancelNotification(context: Context, signalId: String) {
        NotificationManagerCompat.from(context).cancel(signalId.hashCode())
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
