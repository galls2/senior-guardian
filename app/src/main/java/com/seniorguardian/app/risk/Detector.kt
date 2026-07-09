package com.seniorguardian.app.risk

interface Detector {
    suspend fun detect(callInfo: CallInfo): Double
}

class StubDetector : Detector {
    override suspend fun detect(callInfo: CallInfo): Double = 1.0
}
