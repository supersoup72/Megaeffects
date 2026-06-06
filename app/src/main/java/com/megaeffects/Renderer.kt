package com.megaeffects

import android.graphics.Bitmap
import android.util.Log

object Renderer {

    fun renderFrame(project: Project, timeSec: Float): Bitmap {
        val width  = project.width
        val height = project.height
        val rendered = mutableListOf<ByteArray?>()

        for (layer in project.layers) {
            if (!layer.visible) { rendered.add(null); continue }
            if (timeSec < layer.start || timeSec > layer.end) { rendered.add(null); continue }

            val clipTime = timeSec - layer.start

            val pixels: ByteArray = try {
                when {
                    layer.sourceType == "video" && layer.source.isNotBlank() ->
                        RenderEngine.extractVideoFrame(layer.source, clipTime, width, height)
                            ?: RenderEngine.fill(width, height, layer.color)

                    layer.sourceType == "image" && layer.source.isNotBlank() ->
                        RenderEngine.loadImage(layer.source, width, height)
                            ?: RenderEngine.fill(width, height, layer.color)

                    else -> RenderEngine.fill(width, height, layer.color)
                }
            } catch (e: Exception) {
                Log.e("MegaEffects", "Layer ${layer.name} error: ${e.message}")
                RenderEngine.fill(width, height, intArrayOf(60, 60, 60, 255))
            }

            val t = Keyframes.getTransformAtTime(layer, clipTime)
            rendered.add(RenderEngine.transform(pixels, width, height, t))
        }

        val composited = RenderEngine.compositeLayers(rendered, width, height)
        return RenderEngine.rgbaToBitmap(composited, width, height)
    }
}
