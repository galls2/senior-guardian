package com.seniorguardian.app.risk

import android.util.Log

class LoudLogResponder : Responder {
    override fun respond(callInfo: CallInfo) {
        Log.e(
            TAG,
            "\n" + "!".repeat(60) +
                "\n!!! ALERT: risky call detected from ${callInfo.phoneNumber ?: "unknown number"} !!!" +
                "\n" + "!".repeat(60)
        )
    }

    private companion object {
        const val TAG = "LoudLogResponder"
    }
}
