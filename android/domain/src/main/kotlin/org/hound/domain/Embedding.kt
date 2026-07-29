package org.hound.domain

import kotlin.math.sqrt

fun l2Normalize(values: FloatArray): FloatArray {
    require(values.isNotEmpty()) { "Values array cannot be empty" }
    var sumOfSquares = 0.0
    for (v in values) {
        require(v.isFinite()) { "Vector elements must be finite, found $v" }
        sumOfSquares += v.toDouble() * v.toDouble()
    }
    require(sumOfSquares > 0.0) { "Cannot normalize zero vector" }
    val norm = sqrt(sumOfSquares)
    val result = FloatArray(values.size)
    for (i in values.indices) {
        result[i] = (values[i] / norm).toFloat()
    }
    return result
}

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.isNotEmpty() && b.isNotEmpty()) { "Vectors cannot be empty" }
    require(a.size == b.size) { "Vector dimensions must match: ${a.size} != ${b.size}" }

    var dot = 0.0
    var normA = 0.0
    var normB = 0.0

    for (i in a.indices) {
        val valA = a[i]
        val valB = b[i]
        require(valA.isFinite() && valB.isFinite()) { "Vector elements must be finite" }
        dot += valA.toDouble() * valB.toDouble()
        normA += valA.toDouble() * valA.toDouble()
        normB += valB.toDouble() * valB.toDouble()
    }

    require(normA > 0.0 && normB > 0.0) { "Cannot compute similarity for zero vector" }

    val cos = dot / (sqrt(normA) * sqrt(normB))
    return cos.coerceIn(-1.0, 1.0).toFloat()
}
