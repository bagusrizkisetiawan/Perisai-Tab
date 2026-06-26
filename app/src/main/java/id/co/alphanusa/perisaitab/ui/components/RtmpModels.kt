package id.co.alphanusa.perisaitab.ui.components

/**
 * Konfigurasi stream RTMP. Tujuan URL diambil otomatis dari server
 * (getPocInfo), jadi config ini hanya menyimpan preferensi kualitas/bitrate.
 */
data class RTMPConfig(
    val resolutionWidth: Int = 1280,
    val resolutionHeight: Int = 720,
    val bitrateKbps: Int = 2500,
)

/** Resolusi video yang sedang dikirim. */
data class RtmpResolution(val width: Int, val height: Int)

/**
 * Status live RTMP yang ditampilkan di UI. Menggantikan tipe DJI
 * `LiveStreamStatus` agar tidak perlu DJI SDK — field-nya sama dengan
 * yang dibaca komponen tampilan (resolution/fps/vbps/isStreaming).
 */
data class RtmpStreamStatus(
    val isStreaming: Boolean = false,
    val resolution: RtmpResolution? = null,
    val fps: Int = 0,
    val vbps: Int = 0,
)
