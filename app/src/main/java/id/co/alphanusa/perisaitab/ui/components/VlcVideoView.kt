package id.co.alphanusa.perisaitab.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Memutar stream UDP MPEG-TS dari GoPro memakai libVLC.
 *
 * Paket UDP masuk diterima di port 8554 lewat interface USB secara otomatis
 * (tak perlu bind untuk paket masuk), jadi player cukup mendengarkan
 * `udp://@:8554`.
 */
@Composable
fun VlcVideoView(streamUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val libVlc = remember {
        LibVLC(
            context,
            arrayListOf(
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--rtsp-tcp",
                "--network-caching=200",
            ),
        )
    }
    val player = remember { MediaPlayer(libVlc) }
    val videoLayout = remember { VLCVideoLayout(context) }

    DisposableEffect(streamUrl) {
        player.attachViews(videoLayout, null, false, false)
        val media = Media(libVlc, Uri.parse(streamUrl)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=200")
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
        }
        player.media = media
        media.release()
        player.play()

        onDispose {
            player.stop()
            player.detachViews()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            libVlc.release()
        }
    }

    AndroidView(modifier = modifier, factory = { videoLayout })
}
