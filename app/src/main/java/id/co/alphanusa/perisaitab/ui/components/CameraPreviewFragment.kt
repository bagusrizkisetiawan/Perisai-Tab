package id.co.alphanusa.perisaitab.ui.components

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.jiangdg.ausbc.CameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.camera.CameraUvcStrategy
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import com.pedro.common.ConnectChecker
import com.pedro.rtmp.rtmp.RtmpClient

/**
 * Preview kamera UVC memakai libausbc (jiangdongguo/AndroidUSBCamera).
 *
 * Selain preview, fragment ini bisa **streaming RTMP**: output encoder H264 dari
 * libausbc ([startPush] + [addEncodeDataCallBack]) di-mux & dikirim lewat
 * [RtmpClient] milik RootEncoder. Untuk saat ini video-saja (tanpa audio).
 */
class CameraPreviewFragment : CameraFragment() {

    /** Callback status koneksi kamera: (terhubung?, pesan). */
    var onState: ((Boolean, String) -> Unit)? = null

    /** Callback status RTMP: (live?, loading?, error?). */
    var onRtmpState: ((Boolean, Boolean, String?) -> Unit)? = null

    /** Callback status rekam: (recording?, savedOk?, pesan?). savedOk null = baru mulai. */
    var onRecordState: ((Boolean, Boolean?, String?) -> Unit)? = null

    /** Callback hasil ambil foto: (sukses?, pesan?). */
    var onPhotoState: ((Boolean, String?) -> Unit)? = null

    private var viewContainer: FrameLayout? = null
    private var textureView: AspectRatioTextureView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var lastOpened = false
    private val poll = object : Runnable {
        override fun run() {
            val opened = isCameraOpened()
            if (opened != lastOpened) {
                lastOpened = opened
                onState?.invoke(opened, if (opened) "Connected" else "Menunggu kamera…")
            }
            handler.postDelayed(this, 600)
        }
    }

    // ── RTMP state ───────────────────────────────────────────────────────────
    @Volatile private var rtmpClient: RtmpClient? = null
    @Volatile private var pendingUrl: String? = null
    @Volatile private var videoInfoSent = false
    private var encoder: Nv21H264Encoder? = null
    private var frameCount = 0

    // ── Rekam video (MP4 → galeri) ─────────────────────────────────────────────
    @Volatile private var recorder: Mp4Recorder? = null
    private var recordFile: File? = null

    // Capture loop: ambil frame dari TextureView (yang sudah menampilkan kamera),
    // independen dari callback frame libausbc yang tidak jalan di device ini.
    // Satu loop melayani DUA konsumen: encoder RTMP dan/atau recorder MP4.
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var captureBitmap: Bitmap? = null

    private val captureRunnable = object : Runnable {
        override fun run() {
            val startMs = SystemClock.elapsedRealtime()
            val enc = encoder
            val rec = recorder
            val tv = textureView
            if ((enc != null || rec != null) && tv != null && tv.isAvailable) {
                try {
                    var bmp = captureBitmap
                    if (bmp == null || bmp.width != CAPTURE_WIDTH || bmp.height != CAPTURE_HEIGHT) {
                        bmp = Bitmap.createBitmap(CAPTURE_WIDTH, CAPTURE_HEIGHT, Bitmap.Config.ARGB_8888)
                        captureBitmap = bmp
                    }
                    tv.getBitmap(bmp)
                    frameCount++
                    // Konversi ARGB→NV12 native (libyuv) → cukup ringan untuk 1080p.
                    // getBitmap sekali, di-share ke stream + rekam.
                    enc?.encodeBitmap(bmp)
                    rec?.encodeBitmap(bmp)
                } catch (e: Exception) {
                    Log.e(TAG, "capture error: ${e.message}")
                }
            }
            if (encoder != null || recorder != null) {
                // Pacing yang mengoreksi waktu kerja: jadwal frame berikutnya
                // berdasarkan sisa interval (bukan interval penuh SETELAH kerja),
                // supaya fps mendekati target & tidak patah-patah.
                val elapsed = SystemClock.elapsedRealtime() - startMs
                val next = (FRAME_INTERVAL_MS - elapsed).coerceAtLeast(0L)
                captureHandler?.postDelayed(this, next)
            }
        }
    }

    /** Nyalakan capture loop bila belum jalan (dipakai stream & rekam). */
    private fun ensureCaptureLoop() {
        if (captureThread != null) return
        val th = HandlerThread("uvc-capture").also { it.start() }
        captureThread = th
        val h = Handler(th.looper)
        captureHandler = h
        h.post(captureRunnable)
        Log.d(TAG, "capture loop dimulai @${CAPTURE_FPS}fps")
    }

    /** Matikan capture loop hanya bila tidak ada stream maupun rekam. */
    private fun stopCaptureLoopIfIdle() {
        if (encoder != null || recorder != null) return
        captureHandler?.removeCallbacks(captureRunnable)
        captureHandler = null
        runCatching { captureThread?.quitSafely() }
        captureThread = null
    }

    /** Mulai kirim video kamera USB ke server RTMP. */
    fun startRtmp(url: String) {
        if (rtmpClient != null) return
        if (!isCameraOpened()) {
            onRtmpState?.invoke(false, false, "Kamera belum siap")
            return
        }
        videoInfoSent = false
        frameCount = 0
        pendingUrl = url
        Log.d(TAG, "startRtmp url=$url cameraOpened=${isCameraOpened()}")

        val checker = object : ConnectChecker {
            override fun onConnectionStarted(url: String) {
                Log.d(TAG, "RTMP onConnectionStarted: $url")
            }
            override fun onConnectionSuccess() {
                Log.d(TAG, "RTMP onConnectionSuccess")
                handler.post { onRtmpState?.invoke(true, false, null) }
            }
            override fun onConnectionFailed(reason: String) {
                Log.e(TAG, "RTMP onConnectionFailed: $reason")
                handler.post {
                    onRtmpState?.invoke(false, false, reason)
                    stopRtmp()
                }
            }
            override fun onNewBitrate(bitrate: Long) {}
            override fun onDisconnect() {
                Log.d(TAG, "RTMP onDisconnect")
                handler.post { onRtmpState?.invoke(false, false, null) }
            }
            override fun onAuthError() {
                Log.e(TAG, "RTMP onAuthError")
                handler.post {
                    onRtmpState?.invoke(false, false, "Auth RTMP gagal")
                    stopRtmp()
                }
            }
            override fun onAuthSuccess() {
                Log.d(TAG, "RTMP onAuthSuccess")
            }
        }

        val client = RtmpClient(checker).apply { setOnlyVideo(true) }
        rtmpClient = client
        onRtmpState?.invoke(false, true, null) // loading

        // Buat encoder kita sendiri.
        val enc = Nv21H264Encoder(
            width = CAPTURE_WIDTH,
            height = CAPTURE_HEIGHT,
            fps = CAPTURE_FPS,
            bitrate = DEFAULT_BITRATE,
            onConfig = { sps, pps ->
                if (!videoInfoSent) {
                    videoInfoSent = true
                    Log.d(TAG, "csd → setVideoInfo + connect($pendingUrl)")
                    client.setVideoInfo(sps.duplicate(), pps.duplicate(), null)
                    client.setFps(CAPTURE_FPS)
                    pendingUrl?.let { client.connect(it) }
                }
            },
            onFrame = { buf, info ->
                runCatching { client.sendVideo(buf, info) }
                    .onFailure { Log.e(TAG, "sendVideo error: ${it.message}") }
            },
        )
        val ok = runCatching { enc.start() }.isSuccess
        if (!ok) {
            rtmpClient = null
            onRtmpState?.invoke(false, false, "Encoder gagal dibuat")
            return
        }
        encoder = enc

        // Mulai (atau ikut) capture loop dari TextureView.
        ensureCaptureLoop()
        Log.d(TAG, "encoder start + capture loop @${CAPTURE_FPS}fps")
    }

    /** Hentikan streaming RTMP (preview & rekam tetap jalan). */
    fun stopRtmp() {
        val client = rtmpClient ?: return
        rtmpClient = null
        runCatching { encoder?.stop() }
        encoder = null
        stopCaptureLoopIfIdle()
        runCatching { client.disconnect() }
        videoInfoSent = false
        pendingUrl = null
    }

    /** Mulai merekam video kamera USB ke file MP4 (nanti disimpan ke galeri). */
    fun startRecording() {
        if (recorder != null) return
        if (!isCameraOpened()) {
            onRecordState?.invoke(false, false, "Kamera belum siap")
            return
        }
        val file = File(requireContext().cacheDir, "perisai_rec_${System.currentTimeMillis()}.mp4")
        val rec = Mp4Recorder(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS, DEFAULT_BITRATE, file)
        val ok = runCatching { rec.start() }.isSuccess
        if (!ok) {
            onRecordState?.invoke(false, false, "Recorder gagal dibuat")
            return
        }
        recorder = rec
        recordFile = file
        ensureCaptureLoop()
        onRecordState?.invoke(true, null, null)
        Log.d(TAG, "recording dimulai → ${file.name}")
    }

    /** Ambil satu foto dari frame kamera USB → simpan ke galeri (PERISAI Photo). */
    fun takePhoto() {
        val tv = textureView
        if (tv == null || !tv.isAvailable || !isCameraOpened()) {
            onPhotoState?.invoke(false, "Kamera belum siap")
            return
        }
        val bmp = runCatching { tv.getBitmap(CAPTURE_WIDTH, CAPTURE_HEIGHT) }.getOrNull()
        if (bmp == null) {
            onPhotoState?.invoke(false, "Gagal mengambil frame")
            return
        }
        val ctx = requireContext().applicationContext
        Thread {
            val saved = runCatching {
                saveImageToGallery(ctx, bmp, "PERISAI_${System.currentTimeMillis()}.jpg")
            }.getOrDefault(false)
            handler.post {
                onPhotoState?.invoke(
                    saved,
                    if (saved) "Foto tersimpan (PERISAI Photo)" else "Gagal menyimpan foto"
                )
            }
        }.start()
        Log.d(TAG, "takePhoto: capture ${CAPTURE_WIDTH}x$CAPTURE_HEIGHT, menyimpan…")
    }

    /** Hentikan rekam, finalisasi MP4, lalu simpan ke galeri (background). */
    fun stopRecording() {
        val rec = recorder ?: return
        recorder = null // capture loop berhenti menyuap rec mulai sekarang
        val file = recordFile
        recordFile = null
        stopCaptureLoopIfIdle()

        val ctx = requireContext().applicationContext
        Thread {
            val finalized = runCatching { rec.stop() }.getOrDefault(false)
            val saved = if (finalized && file != null) {
                saveVideoToGallery(ctx, file, "PERISAI_${System.currentTimeMillis()}.mp4")
            } else {
                runCatching { file?.delete() }
                false
            }
            handler.post {
                if (saved) {
                    onRecordState?.invoke(false, true, "Tersimpan di galeri (PERISAI VIDEO)")
                } else {
                    onRecordState?.invoke(false, false, "Gagal menyimpan video")
                }
            }
        }.start()
        Log.d(TAG, "recording dihentikan, menyimpan…")
    }

    /**
     * Set kamera & encoder ke 1280×720 (landscape) SEJAK AWAL. Memakai
     * updateResolution di tengah jalan membuat ukuran encoder kacau (jadi
     * 720×1280 terbalik) → BufferOverflow di encoder video. Dengan menyetel
     * lewat CameraRequest di sini, kamera + encoder konsisten dari pembuatan.
     */
    override fun getCameraClient(): CameraClient {
        return CameraClient.newBuilder(requireContext())
            .setEnableGLES(true)
            // true: aktifkan jalur data mentah (NV21) agar addPreviewDataCallBack
            // benar-benar mengirim frame.
            .setRawImage(true)
            .setCameraStrategy(CameraUvcStrategy(requireContext()))
            .setCameraRequest(
                CameraRequest.Builder()
                    .setPreviewWidth(PREVIEW_WIDTH)
                    .setPreviewHeight(PREVIEW_HEIGHT)
                    .create()
            )
            .setDefaultRotateType(RotateType.ANGLE_0)
            .build()
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        viewContainer = root
        textureView = AspectRatioTextureView(requireContext())
        return root
    }

    override fun getCameraView(): IAspectRatio? = textureView

    override fun getCameraViewContainer(): ViewGroup? = viewContainer

    override fun initView() {
        super.initView()
        handler.post(poll)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(poll)
        stopRtmp()
        stopRecording()
        super.onDestroyView()
    }

    private companion object {
        const val TAG = "UvcRtmp"

        // Resolusi PREVIEW kamera (yang tampil di layar). 720p 16:9.
        // libausbc TIDAK punya setter fps — fps ditentukan kamera per-resolusi.
        // Mayoritas kamera/dongle UVC: 1080p hanya 5-15fps, tapi 720p 30fps.
        // Jadi 720p dipilih agar preview MULUS (30fps), bukan patah-patah.
        const val PREVIEW_WIDTH = 1280
        const val PREVIEW_HEIGHT = 720

        // Resolusi CAPTURE/stream & rekam. Disamakan dengan preview (720p):
        // capture > preview hanya meng-upscale (blur + readback lebih berat).
        const val CAPTURE_WIDTH = 1280
        const val CAPTURE_HEIGHT = 720

        // Bitrate untuk 720p: 4 Mbps sudah tajam untuk live & rekam.
        const val DEFAULT_BITRATE = 4_000_000

        // FPS target capture dari TextureView. 30fps agar lebih mulus; pacing di
        // captureRunnable otomatis menyesuaikan bila hardware tak sanggup sepenuhnya.
        const val CAPTURE_FPS = 30
        const val FRAME_INTERVAL_MS = 1000L / CAPTURE_FPS
    }
}
