package com.seniorguardian.app.risk

interface Responder {
    val severityThreshold: Double
    fun respond(callInfo: CallInfo)
}
