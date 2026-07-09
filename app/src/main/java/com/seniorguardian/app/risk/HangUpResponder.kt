package com.seniorguardian.app.risk

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.seniorguardian.app.config.AppConfig
import com.seniorguardian.app.telecom.CallBridge

class HangUpResponder : Responder {
    override val severityThreshold: Double = AppConfig.HANGUP_SEVERITY_THRESHOLD

    override fun respond(callInfo: CallInfo) {
        Handler(Looper.getMainLooper()).post {
            val call = CallBridge.currentCall.value
            if (call == null) {
                Log.w(TAG, "asked to hang up but there is no current call")
                return@post
            }
            Log.e(TAG, "hanging up call from ${callInfo.phoneNumber ?: "unknown number"} — severity crossed hang-up threshold")
            call.disconnect()
        }
    }

    private companion object {
        const val TAG = "HangUpResponder"
    }
}
