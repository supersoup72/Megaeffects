package com.megaeffects

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.util.Log

object RenderEngine {

    private var engineLoaded = false

    init {
        try {
            System.loadLibrary("render_engine")
            engineLoaded = true
            Log.i("MegaEffects", "Render engine: ${nativeVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("MegaEffects", "Render engine not available: ${e.message}")
        }
    }

    fun isAvailable() = engineLoaded

    private external fun nativeComposite(canvas: ByteArray, layer: ByteArray, width: Int, height: Int)
    private external fun nativeTransform(
        src: ByteArray, width: Int, height: Int,
        tx: Float, ty: Float,
        scaleX: Float, scaleY: Float,
        rotZ: Float, rotX: Float, rotY: Float,
        opacity: Float, perspective: Float
    ): ByteArray
    private external fun nativeFill(width: Int, height: Int, r: Int, g: Int, b: Int, a: Int): ByteArray
    external fun nativeVersion(): String

    // ── Public API ────────────────────────────────────────────────────────────

    fun composite(canvas: ByteArray, layer: ByteArray, width: Int, height: Int): ByteArray {
        return if (engineLoaded) {
            val result = canvas.copyOf()
            nativeComposite(result, layer, width, height)
            result
        } else {
            compositeFallback(canvas, layer, width, height)
        }
    }

    fun transform(pixels: ByteArray, width: Int, height: Int, t: Transform): ByteArray {
        val tx = t.x / width
        val ty = t.y / height
        return if (engineLoaded) {
            nativeTransform(pixels, width, height, tx, ty,
                t.scaleX, t.scaleY, t.rotateZ, t.rotateX, t.rotateY,
                t.opacity, t.perspective)
        } else pixels
    }

    fun fill(width: Int, height: Int, color: IntArray): ByteArray {
        val r = color.getOrElse(0) { 60 }
        val g = color.getOrElse(1) { 60 }
        val b = color.getOrElse(2) { 60 }
        val a = color.getOrElse(3) { 255 }
        return if (engineLoaded) {
            nativeFill(width, height, r, g, b, a)
        } else {
            ByteArray(width * height * 4).also { buf ->
                for (i in 0 until width * height) {
                    buf[i*4]=r.toByte(); buf[i*4+1]=g.toByte()
                    buf[i*4+2]=b.toByte(); buf[i*4+3]=a.toByte()
                }
            }
        }
    }

    fun compositeLayers(layers: List<ByteArray?>, width: Int, height: Int): ByteArray {
        var canvas = fill(width, height, intArrayOf(0, 0, 0, 255))
        for (layer in layers) {
            if (layer != null) canvas = composite(canvas, layer, width, height)
        }
        return canvas
    }

    // ── Frame extraction ──────────────────────────────────────────────────────

    fun extractVideoFrame(path: String, timeSec: Float, width: Int, height: Int): ByteArray? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val timeUs = (timeSec * 1_000_000).toLong()
            val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            bitmap?.let { bitmapToRgba(it, width, height) }
        } catch (e: Exception) {
            Log.e("MegaEffects", "Video frame error: ${e.message}")
            null
        }
    }

    fun loadImage(path: String, width: Int, height: Int): ByteArray? {
        return try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bitmap = BitmapFactory.decodeFile(path, opts) ?: run {
                Log.e("MegaEffects", "Failed to decode image: $path")
                return null
            }
            bitmapToRgba(bitmap, width, height)
        } catch (e: Exception) {
            Log.e("MegaEffects", "Image load error: ${e.message}")
            null
        }
    }

    fun bitmapToRgba(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        // Scale bitmap to target size
        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else bitmap

        // Ensure ARGB_8888 format
        val argb = if (scaled.config != Bitmap.Config.ARGB_8888) {
            scaled.copy(Bitmap.Config.ARGB_8888, false)
        } else scaled

        val intBuf = IntArray(width * height)
        argb.getPixels(intBuf, 0, width, 0, 0, width, height)

        // Convert Android ARGB to RGBA bytes
        val buf = ByteArray(width * height * 4)
        for (i in intBuf.indices) {
            val px = intBuf[i]
            buf[i * 4]     = ((px shr 16) and 0xFF).toByte() // R
            buf[i * 4 + 1] = ((px shr 8)  and 0xFF).toByte() // G
            buf[i * 4 + 2] = (px          and 0xFF).toByte() // B
            buf[i * 4 + 3] = ((px shr 24) and 0xFF).toByte() // A
        }
        return buf
    }

    fun rgbaToBitmap(pixels: ByteArray, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val intBuf = IntArray(width * height)
        for (i in intBuf.indices) {
            val r = pixels[i * 4    ].toInt() and 0xFF
            val g = pixels[i * 4 + 1].toInt() and 0xFF
            val b = pixels[i * 4 + 2].toInt() and 0xFF
            val a = pixels[i * 4 + 3].toInt() and 0xFF
            intBuf[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(intBuf, 0, width, 0, 0, width, height)
        return bmp
    }

    // ── Fallback compositor ───────────────────────────────────────────────────

    private fun compositeFallback(canvas: ByteArray, src: ByteArray, width: Int, height: Int): ByteArray {
        val dst = canvas.copyOf()
        val n = width * height
        for (i in 0 until n) {
            val si = i * 4
            val sa = src[si + 3].toInt() and 0xFF
            if (sa == 0) continue
            val da = dst[si + 3].toInt() and 0xFF
            if (sa == 255) {
                dst[si]=src[si]; dst[si+1]=src[si+1]; dst[si+2]=src[si+2]; dst[si+3]=src[si+3]
                continue
            }
            val oa = sa + da - (sa * da + 127) / 255
            if (oa == 0) { dst[si+3] = 0; continue }
            for (c in 0..2) {
                val sv = src[si+c].toInt() and 0xFF
                val dv = dst[si+c].toInt() and 0xFF
                dst[si+c] = ((sv*sa + dv*da - dv*da*sa/255 + oa/2) / oa).coerceIn(0,255).toByte()
            }
            dst[si+3] = oa.coerceIn(0, 255).toByte()
        }
        return dst
    }
}
