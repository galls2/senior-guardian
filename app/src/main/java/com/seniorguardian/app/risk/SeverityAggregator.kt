package com.seniorguardian.app.risk

interface SeverityAggregator {
    fun aggregate(scores: List<Double>): Double
}

class AverageSeverityAggregator : SeverityAggregator {
    override fun aggregate(scores: List<Double>): Double =
        if (scores.isEmpty()) 0.0 else scores.sum() / scores.size
}
