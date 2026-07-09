package com.seniorguardian.app.risk

import com.seniorguardian.app.config.AppConfig

interface ActionPolicy {
    fun decide(severity: Double): GuardianAction
}

class ThresholdActionPolicy(
    private val threshold: Double = AppConfig.ALERT_SEVERITY_THRESHOLD
) : ActionPolicy {
    override fun decide(severity: Double): GuardianAction =
        if (severity >= threshold) GuardianAction.Alert else GuardianAction.NoAction
}
