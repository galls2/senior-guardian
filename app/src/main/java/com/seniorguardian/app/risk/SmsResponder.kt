package com.seniorguardian.app.risk

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.seniorguardian.app.config.AppConfig

class SmsResponder(private val context: Context) : Responder {
    override fun respond(callInfo: CallInfo) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        AppConfig.SMS_RECIPIENTS.forEach { recipient ->
            smsManager.sendTextMessage(recipient, null, AppConfig.SMS_MESSAGE_BODY, null, null)
            Log.d(TAG, "sent SMS \"${AppConfig.SMS_MESSAGE_BODY}\" to $recipient (triggered by callInfo=$callInfo)")
        }
    }

    private companion object {
        const val TAG = "SmsResponder"
    }
}
