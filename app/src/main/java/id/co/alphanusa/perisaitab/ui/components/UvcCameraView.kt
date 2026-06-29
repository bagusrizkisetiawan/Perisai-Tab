package id.co.alphanusa.perisaitab.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * @param onState callback (terhubung?, pesan) untuk update status di UI.
 */
@Composable
fun UvcCameraView(
    modifier: Modifier = Modifier,
    onState: (Boolean, String) -> Unit = { _, _ -> },
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
                val fragment = CameraPreviewFragment().apply { this.onState = onState }
                fm.beginTransaction()
                    .replace(view.id, fragment)
                    .commitAllowingStateLoss()
            } else {
                existing.onState = onState
            }
        },
    )

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
