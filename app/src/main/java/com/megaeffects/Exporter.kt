package com.megaeffects

import android.graphics.Bitmap
import android.media.*
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object Exporter {

    suspend fun export(
        project: Project,
        outputPath: String,
        onProgress: (Float, String) -> Unit
    ): Pair<Boolean, String> {
        val fps      = project.fps
        val duration = project.duration
        val width    = project.width
        val height   = project.height
        val total    = (duration * fps).toInt()

        return try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            val muxer  = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val codec  = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()

            var trackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            for (frameNum in 0 until total) {
                val timeSec = frameNum.toFloat() / fps
                val bitmap  = Renderer.renderFrame(project, timeSec)

                // Draw bitmap to surface
                val canvas = surface.lockCanvas(null)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                surface.unlockCanvasAndPost(canvas)

                // Drain encoder
                while (true) {
                    val idx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        idx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        idx >= 0 -> {
                            val buf = codec.getOutputBuffer(idx)!!
                            bufferInfo.presentationTimeUs = (frameNum * 1_000_000L / fps)
                            if (muxerStarted && bufferInfo.size > 0) {
                                muxer.writeSampleData(trackIndex, buf, bufferInfo)
                            }
                            codec.releaseOutputBuffer(idx, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }

                onProgress(
                    (frameNum + 1).toFloat() / total,
                    "Frame ${frameNum + 1}/$total"
                )
            }

            // Signal EOS
            codec.signalEndOfInputStream()

            // Drain remaining
            while (true) {
                val idx = codec.dequeueOutputBuffer(bufferInfo, 100_000)
                if (idx >= 0) {
                    val buf = codec.getOutputBuffer(idx)!!
                    if (muxerStarted && bufferInfo.size > 0) {
                        bufferInfo.presentationTimeUs = (total * 1_000_000L / fps)
                        muxer.writeSampleData(trackIndex, buf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break
            }

            codec.stop(); codec.release()
            surface.release()
            muxer.stop(); muxer.release()

            Pair(true, "Saved to:\n$outputPath")
        } catch (e: Exception) {
            Log.e("MegaEffects", "Export error: ${e.message}", e)
            Pair(false, "Export failed:\n${e.message}")
        }
    }
}
