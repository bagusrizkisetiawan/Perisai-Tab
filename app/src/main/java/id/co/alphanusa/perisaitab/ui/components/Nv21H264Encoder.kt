package id.co.alphanusa.perisaitab.ui.components

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer

/**
 * Encoder H.264 mandiri memakai [MediaCodec] (byte-buffer mode).
 *
 * Dipakai untuk meng-encode frame NV21 dari kamera USB (libausbc) menjadi H264,
 * MENGGANTIKAN encoder internal libausbc yang error (BufferOverflow) di sebagian
 * chip. Output di-callback ke pemanggil untuk dikirim ke RTMP.
 *
 * @param onConfig dipanggil sekali saat SPS/PPS siap.
 * @param onFrame  dipanggil tiap frame H264 ter-encode.
 */
class Nv21H264Encoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitrate: Int,
    private val onConfig: (sps: ByteBuffer, pps: ByteBuffer) -> Unit,
    private val onFrame: (buf: ByteBuffer, info: MediaCodec.BufferInfo) -> Unit,
) {
    private var codec: MediaCodec? = null
    private var nv12: ByteArray = ByteArray(width * height * 3 / 2)
    private var nv12Argb: ByteArray = ByteArray(width * height * 3 / 2)
    private var startNs = 0L
    @Volatile private var running = false

    fun start() {
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        codec = MediaCodec.createEncoderByType(MIME).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        startNs = System.nanoTime()
        running = true
        Log.d(TAG, "encoder start ${width}x$height @${fps}fps ${bitrate}bps")
    }

    /** Suapkan satu frame NV21 (panjang = w*h*3/2). */
    fun encode(nv21: ByteArray) {
        if (!running) return
        if (nv12.size != nv21.size) nv12 = ByteArray(nv21.size)
        nv21ToNv12(nv21, nv12, width, height)
        feed(nv12)
    }

    /** Suapkan satu frame ARGB_8888 (panjang = w*h piksel). */
    fun encodeArgb(argb: IntArray) {
        if (!running) return
        argbToNv12(argb, width, height, nv12Argb)
        feed(nv12Argb)
    }

    private fun feed(yuv: ByteArray) {
        val c = codec ?: return
        try {
            val inIndex = c.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                val input = c.getInputBuffer(inIndex)
                if (input != null) {
                    input.clear()
                    input.put(yuv)
                    val ptsUs = (System.nanoTime() - startNs) / 1000
                    c.queueInputBuffer(inIndex, 0, yuv.size, ptsUs, 0)
                }
            }
            drain(c)
        } catch (e: Exception) {
            Log.e(TAG, "encode error: ${e.message}")
        }
    }

    private fun drain(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIndex = c.dequeueOutputBuffer(info, 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = c.outputFormat
                    val sps = f.getByteBuffer("csd-0")
                    val pps = f.getByteBuffer("csd-1")
                    if (sps != null && pps != null) {
                        Log.d(TAG, "csd siap (SPS+PPS)")
                        onConfig(sps, pps)
                    }
                }
                outIndex >= 0 -> {
                    val out = c.getOutputBuffer(outIndex)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (out != null && info.size > 0 && !isConfig) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        onFrame(out, info)
                    }
                    c.releaseOutputBuffer(outIndex, false)
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }

    /** ARGB_8888 → NV12 (BT.601 limited range). */
    private fun argbToNv12(argb: IntArray, w: Int, h: Int, out: ByteArray) {
        var yIdx = 0
        var uvIdx = w * h
        for (j in 0 until h) {
            val even = j and 1 == 0
            for (i in 0 until w) {
                val c = argb[j * w + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                out[yIdx++] = y.coerceIn(0, 255).toByte()
                if (even && (i and 1 == 0) && uvIdx + 1 < out.size) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    out[uvIdx++] = u.coerceIn(0, 255).toByte()
                    out[uvIdx++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
    }

    /** NV21 (Y + V,U interleaved) → NV12 (Y + U,V interleaved). */
    private fun nv21ToNv12(nv21: ByteArray, out: ByteArray, w: Int, h: Int) {
        val ySize = w * h
        System.arraycopy(nv21, 0, out, 0, minOf(ySize, nv21.size))
        var i = ySize
        while (i + 1 < nv21.size && i + 1 < out.size) {
            out[i] = nv21[i + 1]     // U
            out[i + 1] = nv21[i]     // V
            i += 2
        }
    }

    private companion object {
        const val TAG = "UvcRtmp"
        const val MIME = "video/avc"
    }
}
