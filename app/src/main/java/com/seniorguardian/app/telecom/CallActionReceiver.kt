package com.seniorguardian.app.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.VideoProfile
import android.util.Log

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val call = CallBridge.currentCall.value
        if (call == null) {
            Log.w(TAG, "received ${intent.action} but there is no current call")
            return
        }
        when (intent.action) {
            ACTION_ANSWER -> {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                context.startActivity(
                    Intent(context, InCallUiActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            ACTION_DECLINE -> call.reject(false, null)
            ACTION_HANGUP -> call.disconnect()
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.seniorguardian.app.telecom.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.seniorguardian.app.telecom.ACTION_DECLINE"
        const val ACTION_HANGUP = "com.seniorguardian.app.telecom.ACTION_HANGUP"
        private const val TAG = "CallActionReceiver"
    }
}
