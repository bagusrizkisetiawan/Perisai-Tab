package id.co.alphanusa.perisaitab.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import io.github.crow_misia.libyuv.AbgrBuffer
import io.github.crow_misia.libyuv.Nv12Buffer
import java.io.File
import java.nio.ByteBuffer

/**
 * Perekam video H.264 → MP4 memakai [MediaCodec] + [MediaMuxer] (byte-buffer mode).
 *
 * Frame di-suap sebagai [Bitmap] (hasil TextureView.getBitmap kamera USB), lalu
 * dikonversi ARGB→NV12 secara native (libyuv) dan di-encode ke file MP4 sementara.
 * Setelah [stop], file dipindah ke galeri lewat [saveVideoToGallery].
 *
 * Dibuat karena encoder internal libausbc rusak di chip ini — jadi perekaman
 * memakai jalur capture-TextureView yang sama dengan streaming RTMP.
 */
class Mp4Recorder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitrate: Int,
    private val outputFile: File,
) {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var startNs = 0L
    @Volatile private var running = false

    // Buffer libyuv (konversi native ARGB→NV12), dialokasi sekali.
    private var abgrBuf: AbgrBuffer? = null
    private var nv12Buf: Nv12Buffer? = null

    fun start() {
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec = MediaCodec.createEncoderByType(MIME).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        startNs = System.nanoTime()
        running = true
        Log.d(TAG, "recorder start ${width}x$height @${fps}fps → ${outputFile.absolutePath}")
    }

    /** Suapkan satu Bitmap (ARGB_8888). Aman dipanggil dari capture thread. */
    @Synchronized
    fun encodeBitmap(bitmap: Bitmap) {
        if (!running) return
        try {
            val a = abgrBuf ?: AbgrBuffer.Factory.allocate(width, height).also { abgrBuf = it }
            val n = nv12Buf ?: Nv12Buffer.Factory.allocate(width, height).also { nv12Buf = it }
            val src = a.asBuffer()
            src.rewind()
            bitmap.copyPixelsToBuffer(src)
            a.convertTo(n)
            val out = n.asBuffer()
            out.rewind()
            feed(out, eos = false)
        } catch (e: Exception) {
            Log.e(TAG, "encodeBitmap error: ${e.message}")
        }
    }

    private fun feed(buf: ByteBuffer?, eos: Boolean) {
        val c = codec ?: return
        val inIndex = c.dequeueInputBuffer(10_000)
        if (inIndex >= 0) {
            val input = c.getInputBuffer(inIndex)
            var size = 0
            if (input != null) {
                input.clear()
                if (buf != null) {
                    size = buf.remaining()
                    input.put(buf)
                }
            }
            val ptsUs = (System.nanoTime() - startNs) / 1000
            val flags = if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            c.queueInputBuffer(inIndex, 0, size, ptsUs, flags)
        }
        drain(c, eos)
    }

    private fun drain(c: MediaCodec, eos: Boolean) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIndex = c.dequeueOutputBuffer(info, if (eos) 10_000 else 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!eos) return // belum ada output, lanjut frame berikutnya
                    // saat EOS: tetap tunggu sampai flag END_OF_STREAM keluar
                }

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer!!.addTrack(c.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                        Log.d(TAG, "muxer start (track=$trackIndex)")
                    }
                }

                outIndex >= 0 -> {
                    val out = c.getOutputBuffer(outIndex)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (out != null && !isConfig && info.size > 0 && muxerStarted) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        muxer!!.writeSampleData(trackIndex, out, info)
                    }
                    c.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** Selesaikan file. Return true bila MP4 valid tertulis. */
    @Synchronized
    fun stop(): Boolean {
        if (!running) return false
        running = false
        var ok = false
        try {
            codec?.let { feed(null, eos = true) } // flush encoder → tulis sisa frame
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
                ok = true
            }
            runCatching { muxer?.release() }
        } catch (e: Exception) {
            Log.e(TAG, "stop error: ${e.message}")
        } finally {
            codec = null
            muxer = null
            muxerStarted = false
            runCatching { abgrBuf?.close() }
            runCatching { nv12Buf?.close() }
            abgrBuf = null
            nv12Buf = null
        }
        return ok
    }

    private companion object {
        const val TAG = "UvcRec"
        const val MIME = "video/avc"
    }
}

/**
 * Pindahkan [source] (MP4 sementara) ke galeri, folder Movies/PERISAI VIDEO.
 * Dipanggil dari background thread (ada I/O). File [source] dihapus setelahnya.
 */
fun saveVideoToGallery(context: Context, source: File, displayName: String): Boolean {
    if (!source.exists() || source.length() == 0L) return false
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/PERISAI VIDEO",
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: return false
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "PERISAI VIDEO",
            )
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, displayName)
            source.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(
                context, arrayOf(dest.absolutePath), arrayOf("video/mp4"), null,
            )
            true
        }
    } catch (e: Exception) {
        Log.e("UvcRec", "saveVideoToGallery error: ${e.message}")
        false
    } finally {
        runCatching { source.delete() }
    }
}
