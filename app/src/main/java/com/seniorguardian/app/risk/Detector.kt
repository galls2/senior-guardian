package com.seniorguardian.app.risk

interface Detector {
    suspend fun detect(callInfo: CallInfo): Double
}
