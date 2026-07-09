package com.seniorguardian.app.risk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.seniorguardian.app.config.AppConfig

class DangerPopupResponder(private val context: Context) : Responder {
    override val severityThreshold: Double = AppConfig.DANGER_POPUP_SEVERITY_THRESHOLD

    override fun respond(callInfo: CallInfo) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, AppConfig.DANGER_POPUP_TEXT, Toast.LENGTH_LONG).show()
        }
    }
}
