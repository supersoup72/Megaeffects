package com.megaeffects

import android.graphics.Bitmap
import android.util.Log

object Renderer {

    fun renderFrame(project: Project, timeSec: Float): Bitmap {
        val width = project.width
        val height = project.height
        val renderedLayers = mutableListOf<ByteArray?>()

        for (layer in project.layers) {
            if (!layer.visible) { renderedLayers.add(null); continue }
            if (timeSec < layer.start || timeSec > layer.end) {
                renderedLayers.add(null); continue
            }

            val clipTime = timeSec - layer.start
            val clipDuration = layer.end - layer.start

            val pixels: ByteArray = try {
                when (layer.sourceType) {
                    "video" -> RenderEngine.extractVideoFrame(layer.source, clipTime, width, height)
                        ?: RenderEngine.fill(width, height, layer.color)
                    "image" -> RenderEngine.loadImage(layer.source, width, height)
                        ?: RenderEngine.fill(width, height, layer.color)
                    else -> RenderEngine.fill(width, height, layer.color)
                }
            } catch (e: Exception) {
                Log.e("MegaEffects", "Layer ${layer.name} error: ${e.message}")
                RenderEngine.fill(width, height, intArrayOf(60, 60, 60, 255))
            }

            // Apply transform
            val transform = Keyframes.getTransformAtTime(layer, clipTime)
            val transformed = RenderEngine.transform(pixels, width, height, transform)
            renderedLayers.add(transformed)
        }

        val composited = RenderEngine.compositeLayers(renderedLayers, width, height)
        return RenderEngine.rgbaToBitmap(composited, width, height)
    }
}
