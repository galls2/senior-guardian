package com.seniorguardian.app.risk

import android.util.Log
import com.seniorguardian.app.config.AppConfig

class LoudLogResponder : Responder {
    override val severityThreshold: Double = AppConfig.LOUD_LOG_SEVERITY_THRESHOLD

    override fun respond(callInfo: CallInfo) {
        Log.e(TAG, "ALERT: risky call from ${callInfo.phoneNumber ?: "unknown number"}")
    }

    private companion object {
        const val TAG = "LoudLogResponder"
    }
}
