package com.aviator.predictor.utils

import com.aviator.predictor.data.models.SignalStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton bus that lets [NotificationActionReceiver] (a BroadcastReceiver)
 * deliver WIN / LOSS taps to [MainViewModel] while the app is in the foreground.
 */
object PendingSignalAction {

    private val _flow = MutableSharedFlow<Pair<String, SignalStatus>>(extraBufferCapacity = 16)
    val flow = _flow.asSharedFlow()

    fun post(signalId: String, status: SignalStatus) {
        _flow.tryEmit(signalId to status)
    }
}
