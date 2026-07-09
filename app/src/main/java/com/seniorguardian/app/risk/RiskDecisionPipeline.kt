package com.seniorguardian.app.risk

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class RiskEvaluation(val severity: Double)

class RiskDecisionPipeline(
    private val detectors: List<Detector>,
    private val blacklistDetector: Detector,
    private val severityAggregator: SeverityAggregator = AverageSeverityAggregator(),
) {
    suspend fun evaluate(callInfo: CallInfo): RiskEvaluation {
        val severity: Double
        val breakdown: String

        if (blacklistDetector.detect(callInfo) >= 1.0) {
            severity = 1.0
            breakdown = "blacklisted"
        } else {
            val results = coroutineScope {
                detectors.map { detector ->
                    async { detector::class.simpleName to detector.detect(callInfo) }
                }.awaitAll()
            }
            severity = severityAggregator.aggregate(results.map { it.second })
            breakdown = results.joinToString { (name, score) -> "$name=$score" }
        }

        Log.d(TAG, "severity=$severity ($breakdown)")
        return RiskEvaluation(severity)
    }

    private companion object {
        const val TAG = "RiskDecisionPipeline"
    }
}
