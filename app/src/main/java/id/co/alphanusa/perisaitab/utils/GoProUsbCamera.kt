package id.co.alphanusa.perisaitab.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

/**
 * Menghubungkan ke GoPro yang dicolok via USB lalu menyalakan webcam stream.
 *
 * Alur:
 *  1. Temukan [Network] Ethernet — itulah RNDIS/USB GoPro.
 *  2. Hitung IP GoPro (DHCP server di subnet itu, biasanya berakhiran `.51`).
 *  3. Semua request HTTP DIIKAT ke network tsb ([Network.openConnection]) agar
 *     keluar lewat kabel USB, bukan Wi-Fi (ini akar masalah timeout sebelumnya).
 *  4. Aktifkan wired control → mulai webcam → video keluar sebagai MPEG-TS
 *     via UDP port 8554 ([streamUrl]).
 */
class GoProUsbCamera(context: Context) {

    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(ConnectivityManager::class.java)

    /** Jaringan USB yang sedang dipakai, disimpan agar player bisa ikut di-bind. */
    @Volatile var ethernetNetwork: Network? = null
        private set

    sealed interface Result {
        data class Connected(
            val goProIp: String,
            val streamUrl: String,
            val network: Network,
        ) : Result

        data class Failed(val reason: String) : Result
    }

    companion object {
        const val STREAM_PORT = 8554
        const val STREAM_URL = "udp://@:$STREAM_PORT"
        private const val API_PORT = 8080
        private const val TIMEOUT_MS = 4000L
    }

    suspend fun connect(): Result = withContext(Dispatchers.IO) {
        val net = findEthernetNetwork()
            ?: return@withContext Result.Failed(
                "Tidak ada jaringan USB (Ethernet) aktif. Pastikan GoPro tercolok " +
                    "dan menyala. (Cek ulang lewat diagnostik bila perlu.)"
            )
        ethernetNetwork = net

        val goProIp = resolveGoProIp(net)
            ?: return@withContext Result.Failed(
                "Jaringan USB aktif tapi IP GoPro tak bisa dihitung dari subnet."
            )

        val base = "http://$goProIp:$API_PORT"

        // Aktifkan kontrol via kabel USB (klaim koneksi).
        get(net, "$base/gopro/camera/control/wired_usb?p=1")

        // Reset state webcam yang mungkin masih nyangkut dari sesi sebelumnya.
        // GoPro hanya mau start dari state OFF; tanpa reset, start ulang -> HTTP 500.
        get(net, "$base/gopro/webcam/stop")
        get(net, "$base/gopro/webcam/exit")
        delay(700)

        // Mulai webcam: MPEG-TS via UDP port 8554, resolusi 1080 (res=12), wide (fov=0).
        var start = get(
            net,
            "$base/gopro/webcam/start?res=12&fov=0&protocol=TS&port=$STREAM_PORT"
        )

        // Fallback: sebagian model/firmware menolak kombinasi parameter di atas
        // (balas HTTP 500). Coba start dengan parameter default bawaan kamera.
        if (!start.ok) {
            delay(400)
            start = get(net, "$base/gopro/webcam/start")
        }

        if (!start.ok) {
            return@withContext Result.Failed(
                "GoPro ditemukan di $goProIp tapi gagal start webcam: ${start.message}. " +
                    "Coba matikan-nyalakan GoPro lalu colok ulang."
            )
        }

        // Beri waktu kamera masuk mode webcam sebelum player mulai mendengarkan.
        delay(1500)
        Result.Connected(goProIp = goProIp, streamUrl = STREAM_URL, network = net)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val net = ethernetNetwork ?: return@withContext
        val ip = resolveGoProIp(net) ?: return@withContext
        get(net, "http://$ip:$API_PORT/gopro/webcam/stop")
        get(net, "http://$ip:$API_PORT/gopro/webcam/exit")
    }

    // --- internal helpers ---

    private fun findEthernetNetwork(): Network? {
        val manager = cm ?: return null
        return manager.allNetworks.firstOrNull { n ->
            val caps = manager.getNetworkCapabilities(n)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }

    /**
     * IP GoPro = DHCP server di link USB. Bila API tersedia (>=30) pakai
     * `dhcpServerAddress`; jika tidak, turunkan dari IP host (`172.2x.1xx.51`).
     */
    private fun resolveGoProIp(net: Network): String? {
        val lp = cm?.getLinkProperties(net) ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lp.dhcpServerAddress?.hostAddress?.let { return it }
        }

        val hostIp = lp.linkAddresses
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull()
            ?.hostAddress ?: return null

        val parts = hostIp.split(".")
        if (parts.size != 4 || parts[0] != "172") return null
        return "${parts[0]}.${parts[1]}.${parts[2]}.51"
    }

    private data class HttpResult(val ok: Boolean, val message: String)

    private suspend fun get(net: Network, urlStr: String): HttpResult {
        return withTimeoutOrNull(TIMEOUT_MS) {
            try {
                val conn = net.openConnection(URL(urlStr)) as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = TIMEOUT_MS.toInt()
                conn.readTimeout = TIMEOUT_MS.toInt()
                val code = conn.responseCode
                val ok = code in 200..299
                // Untuk error (mis. 500), GoPro biasanya mengirim body JSON berisi
                // alasan. Baca agar muncul di pesan, memudahkan diagnosis.
                val body = runCatching {
                    (if (ok) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.use { it.readText() }?.trim()?.take(200)
                }.getOrNull().orEmpty()
                conn.disconnect()
                HttpResult(ok, "HTTP $code${if (body.isNotEmpty()) " $body" else ""}")
            } catch (e: Exception) {
                HttpResult(false, "${e.javaClass.simpleName}: ${e.message}")
            }
        } ?: HttpResult(false, "timeout")
    }
}
