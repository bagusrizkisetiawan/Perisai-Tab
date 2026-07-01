package id.co.alphanusa.perisaitab.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import id.co.alphanusa.perisaitab.R

/**
 * Menampilkan preview kamera USB (UVC) di dalam Compose dengan meng-host
 * [CameraPreviewFragment] (libausbc) lewat [FragmentContainerView].
 *
 * Activity yang memakai composable ini WAJIB berupa [FragmentActivity].
 *
 * @param onState callback (terhubung?, pesan) status kamera.
 * @param rtmpUrl bila non-null, video kamera dikirim ke URL RTMP ini; null = stop.
 * @param onRtmpState callback (live?, loading?, error?) status streaming RTMP.
 */
@Composable
fun UvcCameraView(
    modifier: Modifier = Modifier,
    onState: (Boolean, String) -> Unit = { _, _ -> },
    rtmpUrl: String? = null,
    onRtmpState: (Boolean, Boolean, String?) -> Unit = { _, _, _ -> },
    isRecording: Boolean = false,
    onRecordState: (Boolean, Boolean?, String?) -> Unit = { _, _, _ -> },
    takePhotoTrigger: Int = 0,
    onPhotoState: (Boolean, String?) -> Unit = { _, _ -> },
) {
    val activity = LocalContext.current as FragmentActivity

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FragmentContainerView(ctx).apply { id = R.id.camera_container }
        },
        update = { view ->
            val fm = activity.supportFragmentManager
            val existing = fm.findFragmentById(view.id) as? CameraPreviewFragment
            if (existing == null) {
                val fragment = CameraPreviewFragment().apply {
                    this.onState = onState
                    this.onRtmpState = onRtmpState
                    this.onRecordState = onRecordState
                    this.onPhotoState = onPhotoState
                }
                fm.beginTransaction()
                    .replace(view.id, fragment)
                    .commitAllowingStateLoss()
            } else {
                existing.onState = onState
                existing.onRtmpState = onRtmpState
                existing.onRecordState = onRecordState
                existing.onPhotoState = onPhotoState
            }
        },
    )

    // Mulai / hentikan RTMP saat rtmpUrl berubah.
    LaunchedEffect(rtmpUrl) {
        val frag = activity.supportFragmentManager
            .findFragmentById(R.id.camera_container) as? CameraPreviewFragment
        if (rtmpUrl != null) frag?.startRtmp(rtmpUrl) else frag?.stopRtmp()
    }

    // Mulai / hentikan rekam saat isRecording berubah.
    LaunchedEffect(isRecording) {
        val frag = activity.supportFragmentManager
            .findFragmentById(R.id.camera_container) as? CameraPreviewFragment
        if (isRecording) frag?.startRecording() else frag?.stopRecording()
    }

    // Ambil foto saat takePhotoTrigger dinaikkan (one-shot).
    LaunchedEffect(takePhotoTrigger) {
        if (takePhotoTrigger <= 0) return@LaunchedEffect
        val frag = activity.supportFragmentManager
            .findFragmentById(R.id.camera_container) as? CameraPreviewFragment
        frag?.takePhoto()
    }

    DisposableEffect(Unit) {
        onDispose {
            val fm = activity.supportFragmentManager
            val frag = fm.findFragmentById(R.id.camera_container)
            if (frag != null && !activity.isFinishing) {
                fm.beginTransaction().remove(frag).commitAllowingStateLoss()
            }
        }
    }
}
