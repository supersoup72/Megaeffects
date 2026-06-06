package com.megaeffects

import java.util.UUID

data class Project(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = "New Project",
    val created: Long = System.currentTimeMillis(),
    var modified: Long = System.currentTimeMillis(),
    var fps: Int = 30,
    var width: Int = 1080,
    var height: Int = 1920,
    var duration: Float = 10f,
    val layers: MutableList<Layer> = mutableListOf()
)

data class Layer(
    val id: String = UUID.randomUUID().toString().take(8),
    var name: String = "Layer",
    var visible: Boolean = true,
    var sourceType: String = "color", // "color", "video", "image"
    var source: String = "",
    var color: IntArray = intArrayOf(60, 60, 60, 255),
    var start: Float = 0f,
    var end: Float = 10f,
    val filters: MutableList<Filter> = mutableListOf(),
    val keyframes: MutableMap<String, MutableList<Keyframe>> = mutableMapOf(),
    // Static transform values (used when no keyframes)
    var x: Float = 0f,
    var y: Float = 0f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var rotateX: Float = 0f,
    var rotateY: Float = 0f,
    var rotateZ: Float = 0f,
    var opacity: Float = 1f,
    var perspective: Float = 500f
)

data class Filter(
    val id: String = UUID.randomUUID().toString().take(8),
    var name: String = "filter",
    var soPath: String = "",
    var sourceCode: String = "",
    var mode: String = "c", // "c" or "glsl"
    var enabled: Boolean = true,
    val params: MutableList<Float> = mutableListOf()
)

data class Keyframe(
    val time: Float,
    var value: Float
)

data class Transform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotateX: Float = 0f,
    val rotateY: Float = 0f,
    val rotateZ: Float = 0f,
    val opacity: Float = 1f,
    val perspective: Float = 500f
)
