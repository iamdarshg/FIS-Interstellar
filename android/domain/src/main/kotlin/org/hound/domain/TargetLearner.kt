package org.hound.domain

object TargetLearner {

    fun buildPrototype(samples: List<FloatArray>): FloatArray {
        require(samples.size in 8..32) {
            "Prototype building requires between 8 and 32 samples, got ${samples.size}"
        }
        val dim = samples[0].size
        require(dim > 0) { "Sample dimension must be greater than 0" }

        for ((idx, sample) in samples.withIndex()) {
            require(sample.size == dim) {
                "Sample at index $idx has dimension ${sample.size}, expected $dim"
            }
            for (v in sample) {
                require(v.isFinite()) { "Sample elements must be finite, found $v at index $idx" }
            }
        }

        val sum = DoubleArray(dim)
        for (sample in samples) {
            for (i in 0 until dim) {
                sum[i] += sample[i].toDouble()
            }
        }

        val avg = FloatArray(dim) { i -> (sum[i] / samples.size).toFloat() }
        return l2Normalize(avg)
    }
}
