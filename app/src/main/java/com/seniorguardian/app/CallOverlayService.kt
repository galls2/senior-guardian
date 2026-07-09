package com.seniorguardian.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.seniorguardian.app.config.AppConfig
import com.seniorguardian.app.risk.CallInfo
import com.seniorguardian.app.risk.Detector
import com.seniorguardian.app.risk.GuardianAction
import com.seniorguardian.app.risk.LoudLogResponder
import com.seniorguardian.app.risk.Responder
import com.seniorguardian.app.risk.RiskDecisionPipeline
import com.seniorguardian.app.risk.SmsResponder
import com.seniorguardian.app.risk.StubDetector
import com.seniorguardian.app.risk.detectors.KnownCallerDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallOverlayService : Service() {

    private var phoneStateReceiver: BroadcastReceiver? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ringingEvaluationJob: Job? = null

    // NOTE: StubDetector always returns 1.0, so even a fully known/recent caller
    // (KnownCallerDetector -> 0.0) still averages to 0.5, meeting the default
    // threshold and triggering Alert. Expected interim state until the stub is
    // replaced by a real detector.
    private val detectors: List<Detector> = listOf(StubDetector(), KnownCallerDetector(this))
    private val responders: List<Responder> = listOf(LoudLogResponder(), SmsResponder(this))
    private val riskDecisionPipeline = RiskDecisionPipeline(detectors = detectors)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        registerPhoneStateReceiver()
    }

    private fun registerPhoneStateReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                    TelephonyManager.EXTRA_STATE_RINGING -> handleRinging(intent, goAsync())
                    TelephonyManager.EXTRA_STATE_IDLE,
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> handleCallEnded()
                }
            }
        }
        phoneStateReceiver = receiver
        registerReceiver(receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
    }

    private fun handleRinging(intent: Intent, pendingResult: BroadcastReceiver.PendingResult) {
        ringingEvaluationJob?.cancel()

        val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val callInfo = CallInfo(phoneNumber = phoneNumber)
        Log.d(TAG, "RINGING received, phoneNumber=$phoneNumber")

        val job = serviceScope.launch {
            try {
                val action = riskDecisionPipeline.evaluate(callInfo)
                Log.d(TAG, "Risk pipeline resolved action=$action")
                withContext(Dispatchers.Main) { applyAction(action, callInfo) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Risk pipeline evaluation failed", e)
            }
        }
        ringingEvaluationJob = job
        job.invokeOnCompletion { pendingResult.finish() }
    }

    private fun applyAction(action: GuardianAction, callInfo: CallInfo) {
        when (action) {
            GuardianAction.Alert -> responders.forEach { responder ->
                Log.d(TAG, "firing responder=${responder::class.simpleName} for callInfo=$callInfo")
                responder.respond(callInfo)
            }
            GuardianAction.NoAction -> Log.d(TAG, "no action for callInfo=$callInfo")
        }
    }

    private fun handleCallEnded() {
        Log.d(TAG, "call ended")
        ringingEvaluationJob?.cancel()
        ringingEvaluationJob = null
    }

    private fun buildNotification(): android.app.Notification {
        val channel = NotificationChannel(
            AppConfig.NOTIFICATION_CHANNEL_ID,
            AppConfig.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, AppConfig.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(AppConfig.NOTIFICATION_TITLE)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        phoneStateReceiver?.let { unregisterReceiver(it) }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val TAG = "CallOverlayService"
    }
}
