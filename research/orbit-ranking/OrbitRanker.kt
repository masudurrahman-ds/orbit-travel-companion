package com.crowncouncil.orbit.ai

import org.json.JSONObject
import kotlin.math.max

/** Pure Kotlin MLP inference. Feature order and scaling must match train_ranker.py. */
class OrbitRanker(modelJson: String) {
    private val root = JSONObject(modelJson)
    private val order = root.getJSONArray("feature_order")
    private val mean = root.getJSONArray("mean")
    private val scale = root.getJSONArray("scale")
    private val layers = root.getJSONArray("layers")

    fun score(features: Map<String, Float>): Float {
        var x = FloatArray(order.length()) { i ->
            val raw = features[order.getString(i)] ?: 0f
            val divisor = scale.getDouble(i).toFloat().let { if (it == 0f) 1f else it }
            (raw - mean.getDouble(i).toFloat()) / divisor
        }
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            when (layer.getString("type")) {
                "linear" -> {
                    val weights = layer.getJSONArray("weights")
                    val bias = layer.getJSONArray("bias")
                    x = FloatArray(weights.length()) { row ->
                        val rowWeights = weights.getJSONArray(row)
                        var sum = bias.getDouble(row).toFloat()
                        for (col in 0 until rowWeights.length()) sum += rowWeights.getDouble(col).toFloat() * x[col]
                        sum
                    }
                }
                "relu" -> x = FloatArray(x.size) { j -> max(0f, x[j]) }
            }
        }
        return x.firstOrNull() ?: 0f // raw logit; apply sigmoid if a probability is needed
    }
}
