package com.seniorguardian.app.risk

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.seniorguardian.app.config.AppConfig

class AlarmSoundResponder(private val context: Context) : Responder {
    override val severityThreshold: Double = AppConfig.ALARM_SOUND_SEVERITY_THRESHOLD

    override fun respond(callInfo: CallInfo) {
        Handler(Looper.getMainLooper()).post {
            try {
                val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.setDataSource(context, uri)
                player.setOnCompletionListener { it.release() }
                player.prepare()
                player.start()
                Log.d(TAG, "playing alarm sound (triggered by callInfo=$callInfo)")
            } catch (e: Exception) {
                Log.e(TAG, "failed to play alarm sound", e)
            }
        }
    }

    private companion object {
        const val TAG = "AlarmSoundResponder"
    }
}
