package id.co.alphanusa.perisaitab.ui.components

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio

/**
 * Preview kamera UVC memakai libausbc (jiangdongguo/AndroidUSBCamera) — library
 * yang dipakai banyak aplikasi "USB Camera".
 *
 * [CameraFragment] menangani semua: deteksi colok, izin USB, buka kamera, pilih
 * resolusi yang didukung, dan render. Kita cukup menyediakan view + container.
 *
 * Di versi 3.2.7 kamera dibuka otomatis dan tidak ada callback state, jadi status
 * "Connected" dipantau dengan polling [isCameraOpened].
 */
class CameraPreviewFragment : CameraFragment() {

    /** Callback ke pemanggil: (terhubung?, pesan). */
    var onState: ((Boolean, String) -> Unit)? = null

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
        super.onDestroyView()
    }
}
