package id.co.alphanusa.perisaitab.utils

import android.content.Context
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

/**
 * Uji kelayakan koneksi GoPro lewat USB pada perangkat Android.
 *
 * Latar belakang: GoPro HERO 9+ yang dicolok via USB-C TIDAK tampil sebagai webcam
 * (UVC). Kamera menyamar sebagai network device (RNDIS / ethernet-over-USB) dengan
 * IP `172.2X.1XX.51`, lalu dikontrol lewat Open GoPro HTTP API (port 8080) dan
 * mengirim video sebagai MPEG-TS via UDP port 8554.
 *
 * Titik kritis di HP non-root: Android sering TIDAK mengangkat interface RNDIS
 * tersebut menjadi network interface. Diagnostik ini memastikan hal itu sebelum
 * membangun fitur penuh.
 */
object GoProUsbDiagnostics {

    /** Vendor ID resmi GoPro untuk perangkat USB-nya. */
    const val GOPRO_VENDOR_ID = 0x2672 // 9842

    private const val API_PORT = 8080
    private const val PROBE_TIMEOUT_MS = 2500L

    enum class Status { OK, WARN, FAIL }

    data class Step(
        val title: String,
        val status: Status,
        val detail: String,
    )

    data class Report(
        val steps: List<Step>,
        /** IP GoPro yang berhasil dijangkau, jika ada. */
        val reachableGoProIp: String?,
    ) {
        val overall: Status
            get() = when {
                steps.any { it.status == Status.FAIL } -> Status.FAIL
                steps.any { it.status == Status.WARN } -> Status.WARN
                else -> Status.OK
            }
    }

    suspend fun run(context: Context): Report = withContext(Dispatchers.IO) {
        val steps = mutableListOf<Step>()

        // --- Langkah 1: apakah GoPro terlihat oleh USB host? ---
        val (usbStep, goProSeen) = checkUsbDevices(context)
        steps += usbStep

        // --- Langkah 2: apakah interface RNDIS / USB-ethernet naik? ---
        val (ifaceStep, candidateIps) = checkRndisInterface()
        steps += ifaceStep

        // --- Langkah 3: apakah Open GoPro API merespon? ---
        var reachableIp: String? = null
        if (candidateIps.isEmpty()) {
            steps += Step(
                title = "3. Open GoPro API merespon?",
                status = Status.FAIL,
                detail = "Dilewati — tidak ada interface USB yang bisa dipakai untuk " +
                    "menghubungi kamera. Selesaikan langkah 2 dulu.",
            )
        } else {
            val probe = probeApi(candidateIps)
            reachableIp = probe.first
            steps += probe.second
        }

        // Catatan kesimpulan bila USB terdeteksi tapi jaringan tidak naik —
        // inilah skenario paling umum di HP konsumen non-root.
        if (goProSeen && candidateIps.isEmpty()) {
            steps += Step(
                title = "Kesimpulan",
                status = Status.WARN,
                detail = "GoPro terdeteksi secara fisik, TAPI Android tidak mengangkat " +
                    "jaringan USB (RNDIS)-nya. Di HP non-root ini lazim terjadi dan tidak " +
                    "ada API publik untuk memaksanya. Opsi: (a) coba HP/Android lain, " +
                    "(b) gunakan jalur Wi-Fi GoPro sebagai fallback.",
            )
        }

        Report(steps = steps, reachableGoProIp = reachableIp)
    }

    private fun checkUsbDevices(context: Context): Pair<Step, Boolean> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return Step(
                "1. GoPro terdeteksi di USB?",
                Status.FAIL,
                "UsbManager tidak tersedia di perangkat ini.",
            ) to false

        val devices = usbManager.deviceList.values
        if (devices.isEmpty()) {
            return Step(
                "1. GoPro terdeteksi di USB?",
                Status.FAIL,
                "Tidak ada perangkat USB sama sekali. Pastikan kabel USB-C mendukung " +
                    "data (bukan kabel charge-only), GoPro menyala, dan mode USB-nya aktif.",
            ) to false
        }

        val goPro = devices.firstOrNull { it.vendorId == GOPRO_VENDOR_ID }
        val listing = devices.joinToString("\n") { d ->
            val tag = if (d.vendorId == GOPRO_VENDOR_ID) "  ◀ GoPro" else ""
            "• ${d.productName ?: "?"} " +
                "(VID=0x%04x PID=0x%04x)$tag".format(d.vendorId, d.productId)
        }

        return if (goPro != null) {
            Step(
                "1. GoPro terdeteksi di USB?",
                Status.OK,
                "GoPro ditemukan (VID 0x2672).\nPerangkat USB:\n$listing",
            ) to true
        } else {
            Step(
                "1. GoPro terdeteksi di USB?",
                Status.WARN,
                "Ada perangkat USB tapi bukan GoPro (VID 0x2672 tidak ditemukan).\n" +
                    "Perangkat USB:\n$listing",
            ) to false
        }
    }

    /**
     * Mencari interface jaringan yang kemungkinan berasal dari USB (RNDIS / NCM /
     * USB-ethernet), lalu menurunkan kandidat IP GoPro (host `.51` di subnet sama).
     */
    private fun checkRndisInterface(): Pair<Step, List<String>> {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces().toList()
        } catch (e: Exception) {
            return Step(
                "2. Interface USB (RNDIS) naik?",
                Status.FAIL,
                "Gagal membaca network interface: ${e.message}",
            ) to emptyList()
        }

        val candidateIps = mutableListOf<String>()
        val lines = mutableListOf<String>()

        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback) continue
            val ipv4 = nif.inetAddresses.toList().filterIsInstance<Inet4Address>()
            if (ipv4.isEmpty()) continue

            val name = nif.name.lowercase()
            val looksUsb = name.startsWith("rndis") || name.startsWith("usb") ||
                name.startsWith("ncm") || name.startsWith("eth")

            for (addr in ipv4) {
                val host = addr.hostAddress ?: continue
                lines += "• ${nif.name} → $host"
                // GoPro USB selalu di blok 172.2x.1xx.51
                val gp = goProIpForHost(host)
                if (gp != null && (looksUsb || host.startsWith("172.2"))) {
                    candidateIps += gp
                }
            }
        }

        val detail = if (lines.isEmpty()) "Tidak ada interface aktif (selain loopback)."
        else "Interface aktif:\n${lines.joinToString("\n")}"

        return when {
            candidateIps.isNotEmpty() -> Step(
                "2. Interface USB (RNDIS) naik?",
                Status.OK,
                "$detail\n\nKandidat IP GoPro: ${candidateIps.distinct().joinToString(", ")}",
            ) to candidateIps.distinct()

            else -> Step(
                "2. Interface USB (RNDIS) naik?",
                Status.FAIL,
                "$detail\n\nTidak ada interface USB/RNDIS di blok 172.2x.1xx. " +
                    "Android belum mengangkat jaringan USB GoPro.",
            ) to emptyList()
        }
    }

    /** Dari IP host `172.2x.1xx.yy`, GoPro berada di `.51` pada subnet yang sama. */
    private fun goProIpForHost(host: String): String? {
        val parts = host.split(".")
        if (parts.size != 4) return null
        if (parts[0] != "172") return null
        val second = parts[1].toIntOrNull() ?: return null
        if (second !in 20..29) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}.51"
    }

    private suspend fun probeApi(candidateIps: List<String>): Pair<String?, Step> {
        val results = mutableListOf<String>()
        for (ip in candidateIps) {
            val (ok, msg) = httpGet("http://$ip:$API_PORT/gopro/version")
            results += "• $ip → $msg"
            if (ok) {
                return ip to Step(
                    "3. Open GoPro API merespon?",
                    Status.OK,
                    "Kamera menjawab di $ip 🎉\n${results.joinToString("\n")}\n\n" +
                        "Langkah berikutnya bisa: aktifkan wired control lalu mulai " +
                        "webcam stream (UDP MPEG-TS port 8554).",
                )
            }
        }
        return null to Step(
            "3. Open GoPro API merespon?",
            Status.FAIL,
            "Tidak ada kandidat IP yang menjawab.\n${results.joinToString("\n")}",
        )
    }

    private suspend fun httpGet(urlStr: String): Pair<Boolean, String> {
        return withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = PROBE_TIMEOUT_MS.toInt()
                    readTimeout = PROBE_TIMEOUT_MS.toInt()
                }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }?.take(200) ?: ""
                conn.disconnect()
                (code in 200..299) to "HTTP $code ${body.trim()}"
            } catch (e: Exception) {
                false to "gagal (${e.javaClass.simpleName}: ${e.message})"
            }
        } ?: (false to "timeout (${PROBE_TIMEOUT_MS}ms)")
    }
}
