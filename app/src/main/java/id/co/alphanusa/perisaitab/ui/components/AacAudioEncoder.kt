package id.co.alphanusa.perisaitab.ui.components

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import java.nio.ByteBuffer

/**
 * Menangkap suara mic HP ([AudioRecord]) lalu meng-encode ke AAC-LC memakai
 * [MediaCodec]. Frame AAC di-callback via [onFrame] untuk dikirim ke RTMP
 * ([com.pedro.rtmp.rtmp.RtmpClient.sendAudio]).
 *
 * Butuh izin RECORD_AUDIO (sudah diminta di awal aplikasi).
 */
class AacAudioEncoder(
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 1,
    private val bitrate: Int = 128_000,
    private val onFrame: (buf: ByteBuffer, info: MediaCodec.BufferInfo) -> Unit,
) {
    val isStereo: Boolean get() = channelCount >= 2

    private var codec: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    private var startNs = 0L
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    fun start() {
        val channelConfig =
            if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT,
        )
        val readSize = maxOf(minBuf, 2048)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            readSize * 2,
        )

        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, readSize * 2)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        startNs = System.nanoTime()
        running = true
        audioRecord?.startRecording()
        thread = Thread { loop(readSize) }.apply {
            name = "aac-audio"
            start()
        }
        Log.d(TAG, "audio encoder start ${sampleRate}Hz ch=$channelCount ${bitrate}bps")
    }

    private fun loop(readSize: Int) {
        val c = codec ?: return
        val ar = audioRecord ?: return
        val pcm = ByteArray(readSize)
        val info = MediaCodec.BufferInfo()
        while (running) {
            val read = ar.read(pcm, 0, pcm.size)
            if (read <= 0) continue
            try {
                val inIndex = c.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val input = c.getInputBuffer(inIndex)
                    if (input != null) {
                        input.clear()
                        input.put(pcm, 0, read)
                        val ptsUs = (System.nanoTime() - startNs) / 1000
                        c.queueInputBuffer(inIndex, 0, read, ptsUs, 0)
                    }
                }
                var outIndex = c.dequeueOutputBuffer(info, 0)
                while (outIndex >= 0) {
                    val out = c.getOutputBuffer(outIndex)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (out != null && info.size > 0 && !isConfig) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        onFrame(out, info)
                    }
                    c.releaseOutputBuffer(outIndex, false)
                    outIndex = c.dequeueOutputBuffer(info, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "audio encode error: ${e.message}")
            }
        }
    }

    fun stop() {
        running = false
        runCatching { thread?.join(500) }
        thread = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }

    private companion object {
        const val TAG = "UvcRtmp"
    }
}
