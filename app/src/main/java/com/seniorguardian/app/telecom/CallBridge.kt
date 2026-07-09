package com.seniorguardian.app.telecom

import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow

object CallBridge {
    val currentCall = MutableStateFlow<Call?>(null)
    val currentCallState = MutableStateFlow<Int?>(null)
    val isMuted = MutableStateFlow(false)
    val isSpeakerOn = MutableStateFlow(false)
    val callerName = MutableStateFlow<String?>(null)
}
