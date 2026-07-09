package com.seniorguardian.app.config

object AppConfig {

    // SmsResponder
    val SMS_RECIPIENTS: List<String> = listOf("+972543375678")
    const val SMS_MESSAGE_BODY: String = "We have detected that your loved one is trageted by scammers :("
    const val SMS_SEVERITY_THRESHOLD: Double = 0.5

    // LoudLogResponder
    const val LOUD_LOG_SEVERITY_THRESHOLD: Double = 0.5

    // DangerPopupResponder
    const val DANGER_POPUP_TEXT: String = "סכנה"
    const val DANGER_POPUP_SEVERITY_THRESHOLD: Double = 0.6

    // HangUpResponder
    // Temporarily disabled for testing: severity maxes out at 1.0, so 1.1 can never be reached.
    // Restore to 0.9 to re-enable.
    const val HANGUP_SEVERITY_THRESHOLD: Double = 1.1

    // KnownCallerDetector
    const val KNOWN_CALLER_LOOKBACK_DAYS: Long = 30

    // BlacklistDetector
    val BLACKLIST_NUMBERS: List<String> = listOf("+972584575582") // stephanie

    // GuardianInCallService: auto-conference trigger
    const val CONFERENCE_CALL_SEVERITY_THRESHOLD: Double = 0.75
    const val CONFERENCE_CALL_NUMBER: String = "+972549995376"

    // GuardianInCallService: how often risk is re-sampled while a call is active
    const val RISK_REFRESH_INTERVAL_MILLIS: Long = 5000
}
