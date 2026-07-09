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

    // Original calls for which a conference attempt has already been made — permanent,
    // one-shot per call. Kept separate from `conferenceAttempt` (below), which only tracks
    // matching the *in-flight* leg and gets cleared once that's resolved; relying on
    // `conferenceAttempt == null` alone as the "already tried" gate let the periodic loop
    // re-fire a new conference call every cycle after a successful merge, since merging
    // doesn't disconnect the original call.
    private val conferencedCalls = mutableSetOf<Call>()

    // Numbers currently associated with an actively-tracked call (the original caller, and
    // the conference number while a leg is in flight or merged). Session-scoped: added when
    // we start tracking a call for a number, removed once that call's session truly ends.
    // This stops the *same logical call* being adopted a second time — and therefore
    // getting its own independent monitoring loop that re-fires the whole pipeline
    // (including a second conference dial) — if Telecom redelivers a new Call object for
    // it mid-session, which has been observed for both the original caller and the
    // conference leg on some OEM stacks. Unlike a permanent record, this is cleared as soon
    // as the session ends, so a genuinely new later call from the same number is never
    // blocked.
    private val activeNumbers = mutableSetOf<String>()

    // Numbers for which a conference merge has already completed successfully — permanent
    // for the life of the service, deliberately not cleared on disconnect. Observed on the
    // real device: once the conference leg merges, the *original* call's own Call object
    // can itself report STATE_DISCONNECTED shortly after (the OEM stack tearing down/
    // replacing the pre-merge connection), which triggers our normal disconnect cleanup and
    // reopens both numbers in `activeNumbers`. Without this permanent record, the self-heal
    // loop then sees Telecom still reporting an active call (the ongoing conference itself)
    // with nothing bridged, and re-adopts it — re-running the entire pipeline, including a
    // second conference dial. Once a number has successfully conferenced, we never
    // re-trigger the pipeline for it again this session.
    private val conferenceCompletedNumbers = mutableSetOf<String>()

    private fun isConferenceCompleted(number: String): Boolean =
        conferenceCompletedNumbers.any { PhoneNumberUtils.compare(number, it) }

    private var conferenceAttempt: ConferenceAttempt? = null

    private data class ConferenceAttempt(val originalCall: Call, val awaitingOutgoingCall: Boolean)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "ready")
        startSelfHealLoop()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallBridge.isMuted.value = audioState.isMuted
    }

    fun toggleMute() {
        val muted = callAudioState?.isMuted ?: false
        setMuted(!muted)
    }

    private fun isNumberActive(number: String): Boolean =
        activeNumbers.any { PhoneNumberUtils.compare(number, it) }

    private fun removeActiveNumber(number: String?) {
        if (number == null) return
        activeNumbers.removeAll { PhoneNumberUtils.compare(number, it) }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val handle = call.details.handle
        if (handle == null) {
            return
        }

        val number = handle.schemeSpecificPart

        if (isConferenceCompleted(number)) {
            Log.d(TAG, "ignoring call event for number with an already-completed conference: $number")
            return
        }

        if (isNumberActive(number)) {
            Log.d(TAG, "ignoring redelivered call event for already-active number: $number")
            return
        }

        val attempt = conferenceAttempt

        if (attempt != null && attempt.awaitingOutgoingCall) {
            if (PhoneNumberUtils.compare(number, AppConfig.CONFERENCE_CALL_NUMBER)) {
                Log.d(TAG, "conference leg added, will merge on answer")
                activeNumbers.add(number)
                conferenceAttempt = attempt.copy(awaitingOutgoingCall = false)
                val originalNumber = attempt.originalCall.details.handle?.schemeSpecificPart
                call.registerCallback(object : Call.Callback() {
                    override fun onStateChanged(call: Call, state: Int) {
                        when (state) {
                            Call.STATE_ACTIVE -> {
                                Log.d(TAG, "merging conference leg")
                                attempt.originalCall.conference(call)
                                conferenceAttempt = null
                                conferenceCompletedNumbers.add(number)
                                if (originalNumber != null) conferenceCompletedNumbers.add(originalNumber)
                                call.unregisterCallback(this)
                            }
                            Call.STATE_DISCONNECTED -> {
                                Log.d(TAG, "conference leg disconnected before/without merging")
                                if (conferenceAttempt?.originalCall === attempt.originalCall) {
                                    conferenceAttempt = null
                                }
                                removeActiveNumber(number)
                                call.unregisterCallback(this)
                            }
                        }
                    }
                })
                return
            } else {
                Log.w(TAG, "call added while awaiting conference leg but number didn't match — treating as unrelated")
            }
        }

        adoptCall(call, number)
    }

    /**
     * Starts tracking a call: bridges it to the UI/notification, and registers the
     * callback that drives the rest of the pipeline once it becomes active.
     */
    private fun adoptCall(call: Call, number: String) {
        Log.d(TAG, "adopting call: $number")
        activeNumbers.add(number)
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

        // If we're adopting a call that's already active (e.g. the self-heal loop catching
        // up on one we missed), registering the callback above won't retroactively fire for
        // the current state — kick off active-call handling directly.
        if (call.state == Call.STATE_ACTIVE) {
            handleActive(call, callInfo)
        }
    }

    /**
     * Safety net: InCallService's own `calls` list is Telecom's authoritative record and
     * can't be affected by any bug in our own bookkeeping above. If we ever end up with no
     * bridged call while Telecom reports one, adopt it directly instead of leaving the UI
     * stuck showing "no active call" indefinitely.
     */
    private fun startSelfHealLoop() {
        serviceScope.launch {
            while (isActive) {
                val telecomCall = calls.firstOrNull { it.details.handle != null }
                val bridgedCall = CallBridge.currentCall.value

                if (telecomCall != null && bridgedCall == null) {
                    val number = telecomCall.details.handle?.schemeSpecificPart
                    if (number != null && !isNumberActive(number) && !isConferenceCompleted(number)) {
                        Log.w(TAG, "self-heal: Telecom reports a call we lost track of ($number), re-adopting")
                        withContext(Dispatchers.Main) { adoptCall(telecomCall, number) }
                    }
                } else if (telecomCall == null && bridgedCall != null) {
                    Log.w(TAG, "self-heal: bridged call no longer known to Telecom, clearing stale reference")
                    withContext(Dispatchers.Main) {
                        CallBridge.currentCall.value = null
                        CallBridge.currentCallState.value = null
                    }
                }

                delay(SELF_HEAL_INTERVAL_MILLIS)
            }
        }
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

                if (result.severity >= AppConfig.CONFERENCE_CALL_SEVERITY_THRESHOLD) {
                    withContext(Dispatchers.Main) {
                        if (conferencedCalls.add(call)) {
                            placeConferenceCall(call)
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
        conferencedCalls.remove(call)
        call.unregisterCallback(callback)
        removeActiveNumber(call.details.handle?.schemeSpecificPart)
        removeActiveNumber(AppConfig.CONFERENCE_CALL_NUMBER) // safe no-op if no conference was involved
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
        conferencedCalls.remove(call)
        removeActiveNumber(call.details.handle?.schemeSpecificPart)
        removeActiveNumber(AppConfig.CONFERENCE_CALL_NUMBER)
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
        private const val SELF_HEAL_INTERVAL_MILLIS = 3000L
    }
}
