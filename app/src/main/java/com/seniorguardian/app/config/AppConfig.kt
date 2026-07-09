package com.seniorguardian.app.config

object AppConfig {

    // SmsResponder
    val SMS_RECIPIENTS: List<String> = listOf("+972544808844")
    const val SMS_MESSAGE_BODY: String = "hi"

    // ThresholdActionPolicy
    const val ALERT_SEVERITY_THRESHOLD: Double = 0.5

    // KnownCallerDetector
    const val KNOWN_CALLER_LOOKBACK_DAYS: Long = 30

    // CallOverlayService foreground notification
    const val NOTIFICATION_CHANNEL_ID: String = "call_monitor_channel"
    const val NOTIFICATION_CHANNEL_NAME: String = "Call monitor"
    const val NOTIFICATION_TITLE: String = "Call monitor active"
}
