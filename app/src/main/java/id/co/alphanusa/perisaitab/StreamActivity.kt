package id.co.alphanusa.perisaitab

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import dev.chrisbanes.haze.HazeState
import id.co.alphanusa.perisaitab.data.local.AppSettingsManager
import id.co.alphanusa.perisaitab.data.remote.api.ApiConfig
import id.co.alphanusa.perisaitab.data.remote.api.ApiService
import id.co.alphanusa.perisaitab.domain.model.BatteryData
import id.co.alphanusa.perisaitab.domain.model.PocData
import id.co.alphanusa.perisaitab.domain.model.getBatteryStatus
import id.co.alphanusa.perisaitab.realtime.CentrifugoClientManager
import id.co.alphanusa.perisaitab.realtime.CentrifugoConnectionState
import id.co.alphanusa.perisaitab.ui.components.CardControlLive
import id.co.alphanusa.perisaitab.ui.components.DialogCall
import id.co.alphanusa.perisaitab.ui.components.DialogMap
import id.co.alphanusa.perisaitab.ui.components.OsmdroidMapView
import id.co.alphanusa.perisaitab.ui.components.RTMPConfig
import id.co.alphanusa.perisaitab.ui.components.RTMPControl
import id.co.alphanusa.perisaitab.ui.components.RtmpResolution
import id.co.alphanusa.perisaitab.ui.components.RtmpStreamStatus
import id.co.alphanusa.perisaitab.ui.components.UvcCameraView
import id.co.alphanusa.perisaitab.ui.components.backgroundColor
import id.co.alphanusa.perisaitab.ui.theme.PERISAITABTheme
import id.co.alphanusa.perisaitab.ui.viewmodel.LivekitViewModel
import id.co.alphanusa.perisaitab.ui.viewmodel.LivekitViewModelFactory
import id.co.alphanusa.perisaitab.ui.viewmodel.UserViewModel
import id.co.alphanusa.perisaitab.ui.viewmodel.UserViewModelFactory
import id.co.alphanusa.perisaitab.utils.HuaweiLocationHelper
import id.co.alphanusa.perisaitab.utils.ILocationHelper
import id.co.alphanusa.perisaitab.utils.NativeLocationHelper
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint


// Resolusi & bitrate default untuk streaming (otomatis dipakai saat mulai stream,
// pengguna tidak perlu memilih resolusi).
private const val DEFAULT_STREAM_WIDTH = 1280
private const val DEFAULT_STREAM_HEIGHT = 720
private const val DEFAULT_STREAM_BITRATE = 2_500_000

// Ambang perubahan sudut (derajat) sebelum state sensor di-update. Mengurangi
// recomposition berlebihan dari sensor yang memicu rebuild peta.
private const val SENSOR_UPDATE_THRESHOLD_DEG = 1.5f

/** Status koneksi kamera USB yang ditampilkan ke pengguna. */
private sealed interface UiState {
    data object Disconnected : UiState
    data object Connecting : UiState
    data object Connected : UiState
    data class Error(val message: String) : UiState
}

class StreamActivity : FragmentActivity(), ConnectChecker {
    // =========================================================================
    // 1. PROPERTIES & STATE VARIABLES
    // =========================================================================

    // Services & Managers
    private lateinit var centrifugoManager: CentrifugoClientManager
    private lateinit var locationHelper: ILocationHelper
    private val authManager: ApiConfig by lazy { ApiConfig.getInstance(context = this) }
    private val apiService: ApiService by lazy {
        authManager.apiService
    }

    // Camera & Stream Variables
    private var rtmpCamera: RtmpCamera2? = null
    private var rtmpUrl: String? = null
    private var isStreaming by mutableStateOf(false)
    var isFrontCamera by mutableStateOf(false)

    // Device States (Location, Permissions, Sensors)
    private var currentLocation by mutableStateOf<Location?>(null)
    private var hasPermissions by mutableStateOf(false)
    private var pitch by mutableFloatStateOf(0f)
    private var roll by mutableFloatStateOf(0f)
    private var yaw by mutableFloatStateOf(0f)
    private var batteryLevel by mutableIntStateOf(0)

    // LiveKit
    private val wsURL = "wss://livekit.digicx.id"
    private var livekitShouldConnect by mutableStateOf(false)
    private var livekitIsMuted by mutableStateOf(true)
    private var livekitIsSpeakerMuted by mutableStateOf(false)

    // =========================================================================
    // 2. ANDROID LIFECYCLE
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)


        // Verifikasi izin di awal
        hasPermissions = checkRequiredPermissions()
        if (!hasPermissions) {
            Toast.makeText(this, "Akses ditolak karena izin belum lengkap!", Toast.LENGTH_LONG)
                .show()
            finish()
            return
        }

        centrifugoManager = CentrifugoClientManager.getInstance(this)
        centrifugoManager.startConnection()

        locationHelper = if (isHuaweiDevice()) {
            HuaweiLocationHelper(this)
        } else {
            NativeLocationHelper(this)
        }

        locationHelper.getLastLocation { location ->
            if (location != null) {
                currentLocation = location
            }
        }

        locationHelper.startLocationUpdates { location ->
            currentLocation = location
            sendToCentrifugo()
        }

        //  Setup Service menggunakan ApiConfig
        val authManager = ApiConfig.getInstance(this)
        val livekitApiService = authManager.apiService
        val userApiService = authManager.apiService

        fetchRtmpUrl()

        // OsmDroid
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            val connectionState by centrifugoManager.connectionState.collectAsState()
            val context = LocalContext.current

            // 1. POLLING LOKASI SECARA LIVE (Update setiap 2 detik)
            LaunchedEffect(Unit) {
                while (true) {
                    batteryLevel = getBatteryPercentage()
                    kotlinx.coroutines.delay(2000)
                }
            }

            // 2. LISTENER SENSOR HP (PITCH, ROLL, YAW) SECARA LIVE
            DisposableEffect(Unit) {
                val sensorManager =
                    context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        event?.let {
                            if (it.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                                val rotationMatrix = FloatArray(9)
                                SensorManager.getRotationMatrixFromVector(rotationMatrix, it.values)
                                val orientationValues = FloatArray(3)
                                SensorManager.getOrientation(rotationMatrix, orientationValues)

                                val newYaw =
                                    Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                                val newPitch =
                                    Math.toDegrees(orientationValues[1].toDouble()).toFloat()
                                val newRoll =
                                    Math.toDegrees(orientationValues[2].toDouble()).toFloat()

                                // Throttle: hanya update state (→ recomposition) bila
                                // perubahan cukup besar. Sensor memicu puluhan event/detik;
                                // tanpa throttle, peta dibangun ulang terus → lag.
                                val changed =
                                    kotlin.math.abs(newYaw - yaw) >= SENSOR_UPDATE_THRESHOLD_DEG ||
                                            kotlin.math.abs(newPitch - pitch) >= SENSOR_UPDATE_THRESHOLD_DEG ||
                                            kotlin.math.abs(newRoll - roll) >= SENSOR_UPDATE_THRESHOLD_DEG
                                if (changed) {
                                    yaw = newYaw
                                    pitch = newPitch
                                    roll = newRoll
                                    sendToCentrifugo()
                                }
                            }
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                sensorManager.registerListener(
                    listener,
                    rotationSensor,
                    SensorManager.SENSOR_DELAY_UI
                )

                onDispose {
                    sensorManager.unregisterListener(listener)
                }
            }


            LaunchedEffect(connectionState) {
                when (connectionState) {
                    CentrifugoConnectionState.ERROR,
                    CentrifugoConnectionState.DISCONNECTED -> {
                        centrifugoManager.startConnection()
                    }

                    CentrifugoConnectionState.CONNECTING -> {
                        delay(50000) // tunggu 10 detik
                        // cek lagi, kalau masih CONNECTING berarti timeout
                        if (centrifugoManager.connectionState.value == CentrifugoConnectionState.CONNECTING) {
                            centrifugoManager.stopConnection()
                            delay(1000)
                            centrifugoManager.startConnection()
                        }
                    }

                    else -> Unit
                }
            }

            // 3. TAMPILKAN UI
            PERISAITABTheme {
                SimpleCameraScreen(
                    location = currentLocation,
                    yaw = yaw,
                    connectionState = connectionState,
                    isStreaming = isStreaming,
                    hasPermissions = hasPermissions,
                    isFrontCamera = isFrontCamera,
                    onStartStream = { w, h, br -> startRtmpStream(w, h, br) },
                    onStopStream = { stopRtmpStream() },
                    onSwitchCamera = { switchCamera() },
                    livekitApiService = livekitApiService,
                    userApiService = userApiService,
                    livekitShouldConnect = livekitShouldConnect,
                    livekitIsMuted = livekitIsMuted,
                    livekitIsSpeakerMuted = livekitIsSpeakerMuted,
                    onLivekitConnect = { livekitShouldConnect = true },
                    onLivekitDisconnect = {
                        livekitShouldConnect = false
                        livekitIsMuted = true
                        livekitIsSpeakerMuted = false

                        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        am.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            maxMusic / 2,
                            0
                        ) // restore ke 50%
                    },
                    onLivekitMuteToggle = { livekitIsMuted = !livekitIsMuted },
                    onLivekitSpeakerToggle = { livekitIsSpeakerMuted = !livekitIsSpeakerMuted }
                )
            }
        }
    }

    fun ComponentActivity.requireNeededPermissions(onPermissionsGranted: (() -> Unit)? = null) {
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                // Check if any permissions weren't granted.
                for (grant in grants.entries) {
                    if (!grant.value) {
                        Toast.makeText(
                            this,
                            "Missing permission: ${grant.key}",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }

                // If all granted, notify if needed.
                if (onPermissionsGranted != null && grants.all { it.value }) {
                    onPermissionsGranted()
                }
            }

        val neededPermissions = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            .filter {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) == PackageManager.PERMISSION_DENIED
            }
            .toTypedArray()

        if (neededPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(neededPermissions)
        } else {
            onPermissionsGranted?.invoke()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (isStreaming) rtmpCamera?.stopStream()
        rtmpCamera?.stopPreview()
        locationHelper.stopLocationUpdates()
    }


    // =========================================================================
    // 3. CORE LOGIC (Network, Streaming, Centrifugo)
    // =========================================================================

    private fun fetchRtmpUrl() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.getPocInfo()
                val accessToken = authManager.getCurrentAccessToken()
                val appSettings = AppSettingsManager.getInstance(this@StreamActivity)
                val baseRtmpUrl = appSettings.getRtmpUrl()
                if (response.isSuccessful) {
                    val pocId = response.body()?.data?.ID
                    if (pocId != null) {
                        withContext(Dispatchers.Main) {
                            rtmpUrl =
                                "${baseRtmpUrl}/$pocId?user=drone&pass=$accessToken"
                            Log.d("RTMP_URL", "Berhasil mengambil URL: $rtmpUrl")
                        }
                    } else {
                        showToastOnMain("Data Drone ID kosong")
                    }
                } else {
                    showToastOnMain("Gagal mengambil data drone dari server")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToastOnMain("Error koneksi saat mengambil data drone")
            }
        }
    }

    private fun startRtmpStream(width: Int, height: Int, bitrate: Int) {
        if (isStreaming) return

        val urlToStream = rtmpUrl
        if (urlToStream == null) {
            Toast.makeText(this, "URL RTMP belum siap, silakan tunggu...", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val rotation = CameraHelper.getCameraOrientation(this)
        val prepared = rtmpCamera?.prepareVideo(
            width,
            height,
            30,             // fps
            bitrate,
            2,              // iFrameInterval (detik)
            rotation
        ) == true

        if (prepared) {
            rtmpCamera?.startStream(urlToStream)
            isStreaming = true
        } else {
            Toast.makeText(this, "Gagal menyiapkan video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRtmpStream() {
        if (!isStreaming) return
        rtmpCamera?.stopStream()
        isStreaming = false
        Toast.makeText(this, "RTMP Stream Dihentikan", Toast.LENGTH_SHORT).show()
    }

    private fun sendToCentrifugo() {
        val loc = currentLocation
        if (centrifugoManager.connectionState.value == CentrifugoConnectionState.CONNECTED) {
            val pocData = PocData(
                pitch = pitch.toDouble(),
                roll = roll.toDouble(),
                yaw = yaw.toDouble(),
                aircraftLatitude = loc?.latitude ?: 0.0,
                aircraftLongitude = loc?.longitude ?: 0.0,
                aircraftAltitude = loc?.altitude ?: 0.0,
                homeLatitude = loc?.latitude ?: 0.0,
                homeLongitude = loc?.longitude ?: 0.0,
                gpsSatelliteCount = 0,
                gpsSignalLevel = if (loc != null) "GOOD" else "NO_GPS",
                battery = BatteryData.SingleBatteryState(
                    percentageRemaining = batteryLevel,
                    voltageLevel = 0f,
                    batteryStatus = getBatteryStatus(batteryLevel)
                )
            )
            centrifugoManager.updatePocData(pocData)
        }
    }


    // =========================================================================
    // 4. HARDWARE & PERMISSION HELPERS
    // =========================================================================

    private fun checkRequiredPermissions(): Boolean {
        val camera = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val audio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val location = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return camera && audio && location
    }

    private fun getBatteryPercentage(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun switchCamera() {
        rtmpCamera?.switchCamera()
        isFrontCamera = !isFrontCamera
    }

    private fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("huawei") || brand.contains("huawei")
    }


    // =========================================================================
    // 5. UTILITY FUNCTIONS
    // =========================================================================

    private suspend fun showToastOnMain(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@StreamActivity, message, Toast.LENGTH_SHORT).show()
        }
    }


    // =========================================================================
    // 6. CONNECT CHECKER INTERFACE IMPLEMENTATION
    // =========================================================================

    override fun onConnectionStarted(url: String) = runOnUiThread {
        Toast.makeText(this, "Memulai koneksi...", Toast.LENGTH_SHORT).show()
    }

    override fun onConnectionSuccess() = runOnUiThread {
        Toast.makeText(this, "Berhasil terhubung ke Server RTMP", Toast.LENGTH_SHORT).show()
    }

    override fun onConnectionFailed(reason: String) = runOnUiThread {
        Toast.makeText(this, "Gagal terhubung: $reason", Toast.LENGTH_LONG).show()
        rtmpCamera?.stopStream()
        isStreaming = false
    }

    override fun onNewBitrate(bitrate: Long) {}

    override fun onDisconnect() = runOnUiThread {
        Toast.makeText(this, "Terputus dari Server RTMP", Toast.LENGTH_SHORT).show()
        isStreaming = false
    }

    override fun onAuthError() = runOnUiThread {
        Toast.makeText(this, "Error Autentikasi RTMP", Toast.LENGTH_SHORT).show()
    }

    override fun onAuthSuccess() = runOnUiThread {
        Toast.makeText(this, "Autentikasi RTMP Sukses", Toast.LENGTH_SHORT).show()
    }


    // =========================================================================
    // 7. COMPOSE UI COMPONENTS
    // =========================================================================

    @Composable
    fun SimpleCameraScreen(
        location: Location?,
        yaw: Float,
        connectionState: CentrifugoConnectionState,
        isStreaming: Boolean,
        hasPermissions: Boolean,
        isFrontCamera: Boolean,
        onStartStream: (width: Int, height: Int, bitrate: Int) -> Unit,
        onStopStream: () -> Unit,
        onSwitchCamera: () -> Unit,
        livekitApiService: ApiService,
        userApiService: ApiService,
        livekitShouldConnect: Boolean,
        livekitIsMuted: Boolean,
        livekitIsSpeakerMuted: Boolean,         // ← fix nama konsisten
        onLivekitConnect: () -> Unit,
        onLivekitDisconnect: () -> Unit,
        onLivekitMuteToggle: () -> Unit,
        onLivekitSpeakerToggle: () -> Unit,     // ← fix nama konsisten
    ) {
        val context = LocalContext.current

        val hazeState = remember { HazeState() }
        var showStopStreamDialog by remember { mutableStateOf(false) }

        // Saat tombol stream ditekan: kalau sedang stream -> stop,
        // kalau belum -> langsung mulai stream (resolusi otomatis, tanpa dialog pilih).
        val onStreamClick = {
            if (isStreaming) onStopStream() else onStartStream(
                DEFAULT_STREAM_WIDTH,
                DEFAULT_STREAM_HEIGHT,
                DEFAULT_STREAM_BITRATE
            )
        }

        var showDialogMap by remember { mutableStateOf(false) }
        var splitMapToCamera by remember { mutableStateOf(false) }
        var showRTMPSettingsDialog by remember { mutableStateOf(false) }


        val factory = remember(livekitApiService) { LivekitViewModelFactory(livekitApiService) }
        val livekitViewModel: LivekitViewModel = viewModel(factory = factory)
        val token by livekitViewModel.livekitToken.collectAsState()

        val userFactory = remember(userApiService) { UserViewModelFactory(userApiService) }
        val userViewModel: UserViewModel = viewModel(factory = userFactory, key = "UserViewModel")
        val user by userViewModel.user.collectAsState()

        var listUserSpeaking by remember { mutableStateOf<List<String>>(emptyList()) }



        LaunchedEffect(token) {
            if (!token.isNullOrEmpty()) {
                onLivekitConnect()
            }
        }

        // Status koneksi kamera USB (UVC). Dikelola oleh libausbc via UvcCameraView.
        var state by remember { mutableStateOf<UiState>(UiState.Connecting) }

        // ── State RTMP (kirim video kamera USB → server RTMP) ────────────────
        var savedRTMPConfig by remember { mutableStateOf(RTMPConfig()) }
        var rtmpStreamStatus by remember { mutableStateOf<RtmpStreamStatus?>(null) }
        var rtmpError by remember { mutableStateOf<String?>(null) }
        var isRtmpLoading by remember { mutableStateOf(false) }
        var isRtmpStreaming by remember { mutableStateOf(false) }
        // URL RTMP aktif → memicu UvcCameraView mulai mengirim. null = berhenti.
        var rtmpRestreamUrl by remember { mutableStateOf<String?>(null) }

        fun onSaveRTMPConfig(config: RTMPConfig) {
            savedRTMPConfig = config
        }

        fun onStartRTMP(config: RTMPConfig) {
            if (state !is UiState.Connected) {
                rtmpError = "Kamera USB belum siap."
                return
            }
            val target = this@StreamActivity.rtmpUrl
            if (target.isNullOrEmpty()) {
                rtmpError = "URL RTMP server belum siap, coba lagi sebentar."
                return
            }
            savedRTMPConfig = config
            rtmpError = null
            isRtmpLoading = true
            isRtmpStreaming = false
            rtmpRestreamUrl = target
        }

        val onStopRTMP: () -> Unit = {
            rtmpRestreamUrl = null
            isRtmpStreaming = false
            isRtmpLoading = false
            rtmpStreamStatus = null
            rtmpError = null
        }

        // GeoPoint stabil: identitas hanya berubah saat koordinat benar-benar
        // berubah, supaya OsmdroidMapView tidak re-fire animateTo / rebuild peta
        // pada tiap recomposition (mis. dari perubahan yaw).
        val deviceGeoPoint = remember(location?.latitude, location?.longitude) {
            GeoPoint(location?.latitude ?: -6.9828, location?.longitude ?: 110.4091)
        }


        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .navigationBarsPadding()
        ) {
            // ── Camera Preview ──────────────────────────────────────────────

            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .zIndex(0.1f),
            ) {
                OsmdroidMapView(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .zIndex(if (splitMapToCamera) 1f else 2f),
                    deviceLocation = deviceGeoPoint,
                    deviceMarkerIcon = R.drawable.ic_map,
                    pocYaw = yaw
                )
            }


            Box(
                modifier =
                    Modifier
                        .width(300.dp)
                        .height(184.dp)
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .zIndex(0.2f)

            ) {
                // Preview kamera USB (UVC). Fragment selalu ter-mount agar tidak
                // dibuat ulang; status koneksi memicu overlay placeholder.
                UvcCameraView(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    onState = { connected, _ ->
                        state = if (connected) UiState.Connected else UiState.Connecting
                    },
                    rtmpUrl = rtmpRestreamUrl,
                    onRtmpState = { live, loading, error ->
                        isRtmpStreaming = live
                        isRtmpLoading = loading
                        rtmpError = error
                        rtmpStreamStatus = if (live) {
                            RtmpStreamStatus(
                                isStreaming = true,
                                resolution = RtmpResolution(
                                    DEFAULT_STREAM_WIDTH,
                                    DEFAULT_STREAM_HEIGHT
                                ),
                                fps = 30,
                                vbps = savedRTMPConfig.bitrateKbps,
                            )
                        } else {
                            null
                        }
                        // Jika gagal/terputus, lepas URL agar bisa start ulang.
                        if (!live && !loading) rtmpRestreamUrl = null
                    },
                )

                if (state !is UiState.Connected) {
                    VideoPlaceholder {
                        when (val s = state) {
                            is UiState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Gagal membuka kamera",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    s.message,
                                    color = Color(0xFFFFCDD2),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }

                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.size(12.dp))
                                Text("Colok kamera USB & izinkan akses…", color = Color.White)
                            }
                        }
                    }
                }
            }


            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0.3f)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Column() {
                        CardControlLive(
                            centrifugoManager = centrifugoManager,
                            openSettings = { showRTMPSettingsDialog = true },
                            rtmpStreamStatus = rtmpStreamStatus,
                            rtmpError = rtmpError,
                            isRtmpLoading = isRtmpLoading,
                            isRtmpStreaming = isRtmpStreaming
                        )
                        when (state) {
                            is UiState.Connected -> {
                                Text(
                                    text = "Kamera USB tersambung",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                            }

                            else -> {

                            }
                        }
                    }
                }

//                Box(
//                    modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp)
//                ){
//                    Box(
//                        Modifier
//                            .background(
//                                color = Color.Black.copy(alpha = 0.4f),
//                                shape = RoundedCornerShape(size = 24.dp)
//                            )
//                            .border(
//                                width = 2.dp,
//                                color = if (livekitShouldConnect && !token.isNullOrEmpty()) successColor else colorPrimary,
//                                shape = RoundedCornerShape(size = 24.dp)
//                            )
//                            .clickable {
//                                showDialogCall = true
//                            }
//                            .width(48.dp)
//                            .height(48.dp)
//                            .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
//                    ) {
//                        Image(
//                            modifier = Modifier.align(Alignment.Center),
//                            painter = painterResource(id = if (livekitShouldConnect && !token.isNullOrEmpty()) R.drawable.outline_phone_in_talk_24 else R.drawable.outline_call_24),
//                            contentDescription = null,
//                            colorFilter = ColorFilter.tint(if (livekitShouldConnect && !token.isNullOrEmpty()) successColor else colorPrimary)
//                        )
//                    }
//                }
            }


            RTMPControl(
                isVisible = showRTMPSettingsDialog,
                onDismiss = { showRTMPSettingsDialog = false },
                onConfirm = { config ->
                    onSaveRTMPConfig(config)
                    onStartRTMP(config)
                },
                currentConfig = savedRTMPConfig,
                centrifugoManager = centrifugoManager,
                rtmpStreamStatus = rtmpStreamStatus,
                rtmpError = rtmpError,
                isRtmpLoading = isRtmpLoading,
                isRtmpStreaming = isRtmpStreaming,
                savedRTMPConfig = savedRTMPConfig,
                onStopRTPM = onStopRTMP
            )
        }
    }
}

@Composable
private fun VideoPlaceholder(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Daftarkan receiver dengan flag RECEIVER_NOT_EXPORTED bila didukung (API 33+). */
private fun ContextCompat_registerReceiver(
    context: Context,
    receiver: BroadcastReceiver,
    filter: IntentFilter,
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(receiver, filter)
    }
}
