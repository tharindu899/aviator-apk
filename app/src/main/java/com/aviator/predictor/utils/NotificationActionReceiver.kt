package com.aviator.predictor.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.aviator.predictor.data.models.SignalStatus

/**
 * Receives Win / Loss action button taps from the notification shade.
 * Posts the action to [PendingSignalAction] (picked up by [MainViewModel]
 * if the app is in the foreground) and dismisses the notification.
 *
 * Must be registered in AndroidManifest.xml.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val signalId = intent.getStringExtra(NotificationHelper.EXTRA_SIGNAL_ID) ?: return

        val status = when (intent.action) {
            NotificationHelper.ACTION_WIN  -> SignalStatus.WIN
            NotificationHelper.ACTION_LOSS -> SignalStatus.LOSS
            else -> return
        }

        // Dismiss the notification immediately so the user gets visual feedback
        NotificationManagerCompat.from(context).cancel(signalId.hashCode())

        // Deliver the action to MainViewModel via the shared bus
        PendingSignalAction.post(signalId, status)
    }
}
