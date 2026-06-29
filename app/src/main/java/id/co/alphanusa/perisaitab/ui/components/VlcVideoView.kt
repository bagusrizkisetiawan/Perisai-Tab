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
 *
 * Bila [rtmpUrl] diisi, VLC sekaligus me-relay video ke server RTMP lewat
 * sout `duplicate{dst=display,...}` — satu sumber UDP, dua tujuan: preview di
 * layar + kirim ke RTMP (video di-copy tanpa transcode supaya ringan).
 *
 * @param onPlaying dipanggil saat player mulai memutar (dipakai untuk menandai
 *   stream RTMP sudah jalan).
 * @param onError dipanggil saat player error.
 */
@Composable
fun VlcVideoView(
    streamUrl: String,
    modifier: Modifier = Modifier,
    rtmpUrl: String? = null,
    onPlaying: () -> Unit = {},
    onError: () -> Unit = {},
) {
    val context = LocalContext.current

    val libVlc = remember {
        LibVLC(
            context,
            arrayListOf(
                // Mode low-latency: buffer dibuat sekecil mungkin agar delay minim.
                // Naikkan angka caching bila video patah-patah di jaringan kurang stabil.
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--rtsp-tcp",
                "--network-caching=100",
                "--live-caching=100",
                "--sout-mux-caching=100",
                "--clock-jitter=0",
                "--clock-synchro=0",
            ),
        )
    }
    val player = remember { MediaPlayer(libVlc) }
    val videoLayout = remember { VLCVideoLayout(context) }

    // Recreate media setiap kali sumber ATAU target RTMP berubah
    // (mis. mulai/berhenti relay RTMP).
    DisposableEffect(streamUrl, rtmpUrl) {
        player.attachViews(videoLayout, null, false, false)
        val media = Media(libVlc, Uri.parse(streamUrl)).apply {
            setHWDecoderEnabled(true, false)
            // Buffer kecil = delay minim (low-latency).
            addOption(":network-caching=100")
            addOption(":live-caching=100")
            addOption(":sout-mux-caching=100")
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
            if (!rtmpUrl.isNullOrEmpty()) {
                // duplicate → tampilkan (display) + kirim ke RTMP (flv, copy).
                addOption(
                    ":sout=#duplicate{dst=display," +
                        "dst=std{access=avio,mux=flv,dst=\"$rtmpUrl\"}}"
                )
                addOption(":sout-keep")
            }
        }

        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> onPlaying()
                MediaPlayer.Event.EncounteredError -> onError()
            }
        }
        player.setEventListener(listener)

        player.media = media
        media.release()
        player.play()

        onDispose {
            player.setEventListener(null)
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
