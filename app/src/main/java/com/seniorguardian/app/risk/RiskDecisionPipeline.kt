package com.seniorguardian.app.risk

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class RiskDecisionPipeline(
    private val detectors: List<Detector> = listOf(StubDetector()),
    private val severityAggregator: SeverityAggregator = AverageSeverityAggregator(),
    private val actionPolicy: ActionPolicy = ThresholdActionPolicy(),
) {
    suspend fun evaluate(callInfo: CallInfo): GuardianAction {
        Log.d(TAG, "evaluating callInfo=$callInfo")
        val results = coroutineScope {
            detectors.map { detector ->
                async { detector::class.simpleName to detector.detect(callInfo) }
            }.awaitAll()
        }
        results.forEach { (name, score) -> Log.d(TAG, "detector=$name probability=$score") }
        val severity = severityAggregator.aggregate(results.map { it.second })
        val action = actionPolicy.decide(severity)
        Log.d(TAG, "severity=$severity action=$action")
        return action
    }

    private companion object {
        const val TAG = "RiskDecisionPipeline"
    }
}
