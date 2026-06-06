package com.megaeffects

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Keyframes {

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun bezierEase(t: Float): Float {
        return t * t * (3f - 2f * t)
    }

    fun getValueAtTime(
        keyframes: List<Keyframe>,
        time: Float,
        interpolation: String = "linear"
    ): Float {
        if (keyframes.isEmpty()) return 0f
        val sorted = keyframes.sortedBy { it.time }
        if (time <= sorted.first().time) return sorted.first().value
        if (time >= sorted.last().time) return sorted.last().value

        for (i in 0 until sorted.size - 1) {
            val k0 = sorted[i]
            val k1 = sorted[i + 1]
            if (time in k0.time..k1.time) {
                val dt = k1.time - k0.time
                if (dt == 0f) return k0.value
                var t = (time - k0.time) / dt
                if (interpolation == "bezier") t = bezierEase(t)
                return lerp(k0.value, k1.value, t)
            }
        }
        return sorted.last().value
    }

    fun getTransformAtTime(layer: Layer, time: Float): Transform {
        fun prop(name: String, default: Float): Float {
            val kfs = layer.keyframes[name]
            return if (!kfs.isNullOrEmpty()) getValueAtTime(kfs, time)
            else default
        }
        return Transform(
            x           = prop("x",        layer.x),
            y           = prop("y",        layer.y),
            scaleX      = prop("scaleX",   layer.scaleX),
            scaleY      = prop("scaleY",   layer.scaleY),
            rotateX     = prop("rotateX",  layer.rotateX),
            rotateY     = prop("rotateY",  layer.rotateY),
            rotateZ     = prop("rotateZ",  layer.rotateZ),
            opacity     = prop("opacity",  layer.opacity),
            perspective = prop("perspective", layer.perspective)
        )
    }

    fun addKeyframe(layer: Layer, prop: String, time: Float, value: Float) {
        val kfs = layer.keyframes.getOrPut(prop) { mutableListOf() }
        kfs.removeAll { kotlin.math.abs(it.time - time) < 0.001f }
        kfs.add(Keyframe(time, value))
        kfs.sortBy { it.time }
    }

    fun removeKeyframe(layer: Layer, prop: String, time: Float) {
        layer.keyframes[prop]?.removeAll { kotlin.math.abs(it.time - time) < 0.001f }
    }
}
