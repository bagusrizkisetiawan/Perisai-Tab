package id.co.alphanusa.perisaitab.ui.components

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
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

    // Capture loop: ambil frame dari TextureView (yang sudah menampilkan kamera),
    // independen dari callback frame libausbc yang tidak jalan di device ini.
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var captureBitmap: Bitmap? = null
    private var argbBuf: IntArray? = null

    private val captureRunnable = object : Runnable {
        override fun run() {
            val client = rtmpClient
            val enc = encoder
            val tv = textureView
            if (client != null && enc != null && tv != null && tv.isAvailable) {
                try {
                    var bmp = captureBitmap
                    if (bmp == null || bmp.width != CAPTURE_WIDTH || bmp.height != CAPTURE_HEIGHT) {
                        bmp = Bitmap.createBitmap(CAPTURE_WIDTH, CAPTURE_HEIGHT, Bitmap.Config.ARGB_8888)
                        captureBitmap = bmp
                    }
                    tv.getBitmap(bmp)
                    var buf = argbBuf
                    if (buf == null || buf.size != CAPTURE_WIDTH * CAPTURE_HEIGHT) {
                        buf = IntArray(CAPTURE_WIDTH * CAPTURE_HEIGHT)
                        argbBuf = buf
                    }
                    bmp.getPixels(buf, 0, CAPTURE_WIDTH, 0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT)
                    if (frameCount < 3) Log.d(TAG, "capture frame #$frameCount")
                    frameCount++
                    enc.encodeArgb(buf)
                } catch (e: Exception) {
                    Log.e(TAG, "capture error: ${e.message}")
                }
            }
            if (rtmpClient != null) {
                captureHandler?.postDelayed(this, 1000L / CAPTURE_FPS)
            }
        }
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

        // Mulai capture loop dari TextureView.
        val th = HandlerThread("uvc-capture").also { it.start() }
        captureThread = th
        val h = Handler(th.looper)
        captureHandler = h
        h.post(captureRunnable)
        Log.d(TAG, "encoder start + capture loop dimulai @${CAPTURE_FPS}fps")
    }

    /** Hentikan streaming RTMP (preview tetap jalan). */
    fun stopRtmp() {
        val client = rtmpClient ?: return
        rtmpClient = null
        captureHandler?.removeCallbacks(captureRunnable)
        captureHandler = null
        runCatching { captureThread?.quitSafely() }
        captureThread = null
        runCatching { encoder?.stop() }
        encoder = null
        runCatching { client.disconnect() }
        videoInfoSent = false
        pendingUrl = null
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
        super.onDestroyView()
    }

    private companion object {
        const val TAG = "UvcRtmp"

        // Resolusi PREVIEW kamera (yang tampil di layar). Pakai 720p 16:9 agar
        // preview tajam & tidak jadi kotak 4:3. Ini terpisah dari resolusi capture.
        const val PREVIEW_WIDTH = 1280
        const val PREVIEW_HEIGHT = 720

        // Resolusi CAPTURE/stream (di-encode & dikirim RTMP). Lebih kecil dari
        // preview supaya getBitmap + konversi ARGB→YUV ringan = mulus & delay
        // rendah. Naikkan ke 1280×720 kalau mau stream lebih tajam (lebih berat).
        const val CAPTURE_WIDTH = 960
        const val CAPTURE_HEIGHT = 540
        const val DEFAULT_BITRATE = 2_500_000

        // FPS capture dari TextureView. 24fps kompromi mulus vs beban CPU.
        const val CAPTURE_FPS = 24
    }
}
