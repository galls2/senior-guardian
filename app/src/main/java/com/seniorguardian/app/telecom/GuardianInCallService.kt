package com.seniorguardian.app.telecom

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.seniorguardian.app.config.AppConfig
import com.seniorguardian.app.risk.CallInfo
import com.seniorguardian.app.risk.DangerPopupResponder
import com.seniorguardian.app.risk.HangUpResponder
import com.seniorguardian.app.risk.LoudLogResponder
import com.seniorguardian.app.risk.Responder
import com.seniorguardian.app.risk.RiskDecisionPipeline
import com.seniorguardian.app.risk.SmsResponder
import com.seniorguardian.app.risk.detectors.BlacklistDetector
import com.seniorguardian.app.risk.detectors.KnownCallerDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GuardianInCallService : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val riskDecisionPipeline = RiskDecisionPipeline(
        detectors = listOf(KnownCallerDetector(this)),
        blacklistDetector = BlacklistDetector()
    )
    private val responders: List<Responder> = listOf(
        LoudLogResponder(),
        SmsResponder(this),
        DangerPopupResponder(this),
        HangUpResponder()
    )

    private val handledActiveCalls = mutableSetOf<Call>()
    private val monitoringJobs = mutableMapOf<Call, Job>()

    // Every call currently being tracked (original + any conference leg), keyed by number.
    // Guards against Telecom redelivering onCallAdded for a call we've already processed
    // (observed after a successful conference merge on some OEM stacks).
    private val trackedCalls = mutableMapOf<Call, String>()

    private var conferenceAttempt: ConferenceAttempt? = null

    private data class ConferenceAttempt(val originalCall: Call, val awaitingOutgoingCall: Boolean)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "ready")
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallBridge.isMuted.value = audioState.isMuted
    }

    fun toggleMute() {
        val muted = callAudioState?.isMuted ?: false
        setMuted(!muted)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val handle = call.details.handle
        if (handle == null) {
            return
        }

        val number = handle.schemeSpecificPart

        if (trackedCalls.values.any { PhoneNumberUtils.compare(number, it) }) {
            Log.d(TAG, "ignoring duplicate call event for already-tracked number: $number")
            return
        }

        val attempt = conferenceAttempt

        if (attempt != null && attempt.awaitingOutgoingCall) {
            if (PhoneNumberUtils.compare(number, AppConfig.CONFERENCE_CALL_NUMBER)) {
                Log.d(TAG, "conference leg added, will merge on answer")
                trackedCalls[call] = number
                conferenceAttempt = attempt.copy(awaitingOutgoingCall = false)
                call.registerCallback(object : Call.Callback() {
                    override fun onStateChanged(call: Call, state: Int) {
                        when (state) {
                            Call.STATE_ACTIVE -> {
                                Log.d(TAG, "merging conference leg")
                                attempt.originalCall.conference(call)
                                conferenceAttempt = null
                                call.unregisterCallback(this)
                            }
                            Call.STATE_DISCONNECTED -> trackedCalls.remove(call)
                        }
                    }
                })
                return
            } else {
                Log.w(TAG, "call added while awaiting conference leg but number didn't match — treating as unrelated")
            }
        }

        Log.d(TAG, "incoming call: $number")
        trackedCalls[call] = number
        CallBridge.currentCall.value = call
        CallBridge.currentCallState.value = call.state

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GuardianCallNotifier.showIncoming(this, number)
        } else {
            startActivity(
                Intent(this, InCallUiActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        val callInfo = CallInfo(phoneNumber = number)

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                CallBridge.currentCallState.value = state
                when (state) {
                    Call.STATE_ACTIVE -> handleActive(call, callInfo)
                    Call.STATE_DISCONNECTED -> handleDisconnected(call, this)
                }
            }
        })
    }

    private fun handleActive(call: Call, callInfo: CallInfo) {
        if (!handledActiveCalls.add(call)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GuardianCallNotifier.showOngoing(this, callInfo.phoneNumber ?: "Unknown")
        }
        startActivity(
            Intent(this, InCallUiActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        val firedResponders = mutableSetOf<Responder>()
        val job = serviceScope.launch {
            while (isActive) {
                val result = riskDecisionPipeline.evaluate(callInfo)

                responders.forEach { responder ->
                    if (result.severity >= responder.severityThreshold && firedResponders.add(responder)) {
                        Log.d(TAG, "responder fired: ${responder::class.simpleName}")
                        responder.respond(callInfo)
                    }
                }

                if (result.severity >= AppConfig.CONFERENCE_CALL_SEVERITY_THRESHOLD && conferenceAttempt == null) {
                    withContext(Dispatchers.Main) {
                        if (conferenceAttempt == null) {
                            placeConferenceCall(call)
                        } else {
                            Log.w(TAG, "skipped duplicate conference attempt")
                        }
                    }
                }

                delay(AppConfig.RISK_REFRESH_INTERVAL_MILLIS)
            }
        }
        monitoringJobs[call] = job
    }

    private fun handleDisconnected(call: Call, callback: Call.Callback) {
        Log.d(TAG, "call disconnected")
        monitoringJobs.remove(call)?.cancel()
        handledActiveCalls.remove(call)
        trackedCalls.remove(call)
        call.unregisterCallback(callback)
        if (conferenceAttempt?.originalCall == call) {
            conferenceAttempt = null
        }
        if (CallBridge.currentCall.value == call) {
            CallBridge.currentCall.value = null
            CallBridge.currentCallState.value = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GuardianCallNotifier.cancel(this)
            }
        }
    }

    private fun placeConferenceCall(originalCall: Call) {
        Log.d(TAG, "placing conference call to ${AppConfig.CONFERENCE_CALL_NUMBER}")
        conferenceAttempt = ConferenceAttempt(originalCall, awaitingOutgoingCall = true)
        val telecomManager = getSystemService(TelecomManager::class.java)
        telecomManager.placeCall(Uri.fromParts("tel", AppConfig.CONFERENCE_CALL_NUMBER, null), null)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        monitoringJobs.remove(call)?.cancel()
        handledActiveCalls.remove(call)
        trackedCalls.remove(call)
        if (conferenceAttempt?.originalCall == call) {
            conferenceAttempt = null
        }
        if (CallBridge.currentCall.value == call) {
            CallBridge.currentCall.value = null
            CallBridge.currentCallState.value = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GuardianCallNotifier.cancel(this)
            }
        }
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        var instance: GuardianInCallService? = null
        private const val TAG = "GuardianInCallService"
    }
}
