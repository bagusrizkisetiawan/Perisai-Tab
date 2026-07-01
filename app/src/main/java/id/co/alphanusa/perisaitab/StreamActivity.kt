package id.co.alphanusa.perisaitab

import android.Manifest
import android.content.Context
import android.content.Intent
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
import android.os.Environment
import android.os.StatFs
import android.preference.PreferenceManager
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
import id.co.alphanusa.perisaitab.ui.components.DialogMissionBrief
import id.co.alphanusa.perisaitab.ui.components.OsmdroidMapView
import id.co.alphanusa.perisaitab.ui.components.RTMPConfig
import id.co.alphanusa.perisaitab.ui.components.RTMPControl
import id.co.alphanusa.perisaitab.ui.components.RecordingButton
import id.co.alphanusa.perisaitab.ui.components.RtmpResolution
import id.co.alphanusa.perisaitab.ui.components.RtmpStreamStatus
import id.co.alphanusa.perisaitab.ui.components.TacticalContainer
import id.co.alphanusa.perisaitab.ui.components.UvcCameraView
import id.co.alphanusa.perisaitab.ui.components.backgroundColor
import id.co.alphanusa.perisaitab.ui.components.colorPrimary
import id.co.alphanusa.perisaitab.ui.components.dangerColor
import id.co.alphanusa.perisaitab.ui.components.successColor
import id.co.alphanusa.perisaitab.ui.theme.PERISAITABTheme
import id.co.alphanusa.perisaitab.ui.viewmodel.AuthViewModel
import id.co.alphanusa.perisaitab.ui.viewmodel.LivekitViewModel
import id.co.alphanusa.perisaitab.ui.viewmodel.LivekitViewModelFactory
import id.co.alphanusa.perisaitab.utils.HuaweiLocationHelper
import id.co.alphanusa.perisaitab.utils.ILocationHelper
import id.co.alphanusa.perisaitab.utils.NativeLocationHelper
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import java.util.Locale


// Resolusi default (dipakai hanya untuk menampilkan info status stream di UI;
// stream sebenarnya diambil dari kamera USB via UvcCameraView/CameraPreviewFragment).
private const val DEFAULT_STREAM_WIDTH = 1280
private const val DEFAULT_STREAM_HEIGHT = 720

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

class StreamActivity : FragmentActivity() {

    // =========================================================================
    // 1. PROPERTIES & STATE VARIABLES
    // =========================================================================

    // Services & Managers
    private lateinit var centrifugoManager: CentrifugoClientManager
    private lateinit var locationHelper: ILocationHelper
    private val authManager: ApiConfig by lazy { ApiConfig.getInstance(context = this) }
    private val apiService: ApiService by lazy { authManager.apiService }

    // Stream (RTMP dari kamera USB). URL diambil dari server via fetchRtmpUrl().
    private var rtmpUrl: String? = null

    // Device States (Location, Permissions, Sensors)
    private var currentLocation by mutableStateOf<Location?>(null)
    private var hasPermissions by mutableStateOf(false)
    private var pitch by mutableFloatStateOf(0f)
    private var roll by mutableFloatStateOf(0f)
    private var yaw by mutableFloatStateOf(0f)
    private var batteryLevel by mutableIntStateOf(0)

    // LiveKit
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

        // Setup service (LiveKit pakai ApiService yang sama dengan networking utama).
        val livekitApiService = authManager.apiService

        fetchRtmpUrl()

        // OsmDroid + edge-to-edge (sembunyikan system bars).
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

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

            // 1. POLLING BATERAI SECARA LIVE (Update setiap 2 detik)
            LaunchedEffect(Unit) {
                while (true) {
                    batteryLevel = getBatteryPercentage()
                    delay(2000)
                }
            }

            // 2. LISTENER SENSOR HP (PITCH, ROLL, YAW) SECARA LIVE
            DisposableEffect(Unit) {
                val sensorManager =
                    context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

                // Rotasi layar. Karena app dikunci landscape, biasanya ROTATION_90
                // (atau ROTATION_270 bila HP diputar terbalik). Dipakai untuk remap
                // sumbu sensor agar yaw dihitung relatif LAYAR (landscape), bukan
                // orientasi natural HP (portrait) → arah tidak lagi miring 90°.
                val displayRotation = displayRotation()

                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        event?.let {
                            if (it.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                                val rotationMatrix = FloatArray(9)
                                SensorManager.getRotationMatrixFromVector(rotationMatrix, it.values)

                                // Remap sumbu sesuai rotasi layar (kompensasi landscape).
                                val remapped = FloatArray(9)
                                val (axisX, axisY) = when (displayRotation) {
                                    Surface.ROTATION_90 ->
                                        SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X

                                    Surface.ROTATION_180 ->
                                        SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y

                                    Surface.ROTATION_270 ->
                                        SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X

                                    else ->
                                        SensorManager.AXIS_X to SensorManager.AXIS_Y
                                }
                                SensorManager.remapCoordinateSystem(
                                    rotationMatrix, axisX, axisY, remapped
                                )

                                val orientationValues = FloatArray(3)
                                SensorManager.getOrientation(remapped, orientationValues)

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

            // 3. AUTO-RECONNECT CENTRIFUGO
            LaunchedEffect(connectionState) {
                when (connectionState) {
                    CentrifugoConnectionState.ERROR,
                    CentrifugoConnectionState.DISCONNECTED -> {
                        centrifugoManager.startConnection()
                    }

                    CentrifugoConnectionState.CONNECTING -> {
                        delay(50000) // tunggu, lalu cek timeout
                        if (centrifugoManager.connectionState.value == CentrifugoConnectionState.CONNECTING) {
                            centrifugoManager.stopConnection()
                            delay(1000)
                            centrifugoManager.startConnection()
                        }
                    }

                    else -> Unit
                }
            }

            // 4. TAMPILKAN UI
            PERISAITABTheme {
                SimpleCameraScreen(
                    location = currentLocation,
                    yaw = yaw,
                    connectionState = connectionState,
                    livekitApiService = livekitApiService,
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

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        locationHelper.stopLocationUpdates()
    }

    // =========================================================================
    // 3. CORE LOGIC (Network, Centrifugo)
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
                            rtmpUrl = "${baseRtmpUrl}/$pocId?user=drone&pass=$accessToken"
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

    /** Rotasi layar saat ini (Surface.ROTATION_*), kompatibel lintas versi. */
    private fun displayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
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
    // 6. COMPOSE UI
    // =========================================================================

    @Composable
    fun SimpleCameraScreen(
        location: Location?,
        yaw: Float,
        connectionState: CentrifugoConnectionState,
        livekitApiService: ApiService,
        livekitShouldConnect: Boolean,
        livekitIsMuted: Boolean,
        livekitIsSpeakerMuted: Boolean,
        onLivekitConnect: () -> Unit,
        onLivekitDisconnect: () -> Unit,
        onLivekitMuteToggle: () -> Unit,
        onLivekitSpeakerToggle: () -> Unit,
    ) {
        val context = LocalContext.current

        var showDialogCall by remember { mutableStateOf(false) }
        var splitMapToCamera by remember { mutableStateOf(false) }
        var showRTMPSettingsDialog by remember { mutableStateOf(false) }

        val factory = remember(livekitApiService) { LivekitViewModelFactory(livekitApiService) }
        val livekitViewModel: LivekitViewModel = viewModel(factory = factory)
        val token by livekitViewModel.livekitToken.collectAsState()

        // ── Menu meatball (pojok kanan atas) + logout ────────────────────────
        var showMenu by remember { mutableStateOf(false) }
        var loggingOut by remember { mutableStateOf(false) }
        val authViewModel: AuthViewModel = viewModel()
        val authState by authViewModel.authState.collectAsState()

        // Setelah logout selesai (session cleared), kembali ke MainActivity dan
        // bersihkan back stack agar tidak bisa di-back ke layar stream.
        LaunchedEffect(loggingOut, authState.isLoggedIn) {
            if (loggingOut && !authState.isLoggedIn) {
                val intent = Intent(this@StreamActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }

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

        // ── State rekam video (MP4 → galeri PERISAI VIDEO) ───────────────────
        var isRecording by remember { mutableStateOf(false) }

        // Durasi rekam (detik) — jalan selama isRecording, reset saat berhenti.
        var recordElapsed by remember { mutableIntStateOf(0) }
        LaunchedEffect(isRecording) {
            recordElapsed = 0
            if (isRecording) {
                while (true) {
                    delay(1000)
                    recordElapsed++
                }
            }
        }
        val recordTime = String.format(
            Locale.US, "%02d:%02d", recordElapsed / 60, recordElapsed % 60
        )

        // Dinaikkan tiap tombol kamera ditekan → ambil foto (one-shot).
        var takePhotoTrigger by remember { mutableIntStateOf(0) }

        // Kontrol kamera di atas preview: muncul saat preview ditap, hilang
        // sendiri setelah 5 detik.
        var showPreviewControls by remember { mutableStateOf(false) }

        // Dialog Mission Brief (rundown dari API).
        var showMissionBrief by remember { mutableStateOf(false) }

        // Dinaikkan tiap tombol "my location" ditekan → peta kembali ke lokasi device.
        var recenterTrigger by remember { mutableIntStateOf(0) }

        // Nama peserta yang sedang bicara di room LiveKit (di-update dari RoomScope).
        var speakingNames by remember { mutableStateOf<List<String>>(emptyList()) }

        // Sisa storage HP (di-refresh berkala; berkurang saat merekam).
        var freeStorage by remember { mutableStateOf(freeStorageText()) }
        LaunchedEffect(Unit) {
            while (true) {
                freeStorage = freeStorageText()
                delay(5000)
            }
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
            // ── Peta (background) ────────────────────────────────────────────
            Box(
                modifier = Modifier
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
                    pocYaw = yaw,
                    recenterTrigger = recenterTrigger
                )
            }

            // ── Preview kamera USB (UVC) ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .height(194.dp)
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .zIndex(0.2f)
            ) {
                // Fragment selalu ter-mount agar tidak dibuat ulang; status
                // koneksi memicu overlay placeholder.
                UvcCameraView(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
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
                    isRecording = isRecording,
                    onRecordState = { recording, savedOk, message ->
                        isRecording = recording
                        if (!message.isNullOrEmpty()) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    takePhotoTrigger = takePhotoTrigger,
                    onPhotoState = { _, message ->
                        if (!message.isNullOrEmpty()) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
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
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.size(12.dp))
                                Text(
                                    "Colok kamera USB & izinkan akses…",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Area tap transparan untuk MEMUNCULKAN kontrol (saat tersembunyi).
                if (!showPreviewControls) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showPreviewControls = true }
                    )
                }

                // Overlay kontrol kamera. Muncul saat preview ditap, lalu hilang
                // sendiri setelah 5 detik (atau saat area kosong ditap lagi).
                if (showPreviewControls) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showPreviewControls = false }
                    ) {

                        // Info storage hanya saat TIDAK merekam (saat rekam diganti
                        // timer persisten di bawah).
                        if (!isRecording) {
                            Box(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .background(
                                            Color.Black.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.outline_sd_card_24),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(freeStorage, color = colorPrimary, fontSize = 10.sp)
                                    Text("Free", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RecordingButton(
                                isRecording = isRecording,
                                recordingStatus = if (isRecording) "Recording" else "",
                                onStartRecording = {
                                    if (state !is UiState.Connected) {
                                        Toast.makeText(
                                            context,
                                            "Kamera USB belum siap",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        isRecording = true
                                    }
                                },
                                onStopRecording = { isRecording = false },
                            )

                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .background(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        if (state !is UiState.Connected) {
                                            Toast.makeText(
                                                context,
                                                "Kamera USB belum siap",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            takePhotoTrigger++
                                        }
                                    }
                                    .width(56.dp)
                                    .height(56.dp)
                                    .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
                            ) {
                                Image(
                                    modifier = Modifier.align(Alignment.Center),
                                    painter = painterResource(id = R.drawable.outline_photo_camera_24),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(colorPrimary)
                                )
                            }
                        }
                    }

                    // Timer auto-hide: reset tiap kali kontrol muncul.
                    LaunchedEffect(Unit) {
                        delay(5000)
                        showPreviewControls = false
                    }
                }

                // Saat merekam: timer PERSISTEN (tidak ikut hilang 5 detik) +
                // border danger mengelilingi preview. Digambar paling atas.
                if (isRecording) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dangerColor)
                        )
                        Text(recordTime, color = Color.White, fontSize = 10.sp)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = 3.dp,
                                color = dangerColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                }
            }

            // ── Overlay kontrol (di atas peta) ───────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0.3f)
            ) {
                // Kiri atas: status live + info kamera
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TacticalContainer(
                                modifier = Modifier.size(48.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = "Utara", fontSize = 8.sp, color = Color.White)
                                    Icon(
                                        painter = painterResource(R.drawable.ic_compas),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.Unspecified
                                    )
                                }
                            }

                            TacticalContainer(
                                modifier = Modifier.height(48.dp),
                                onClick = { showMissionBrief = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 22.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_target_arrow_24_filled),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = Color.Unspecified
                                    )
                                    Text(text = "Mission Brief", fontSize = 12.sp, color = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                }
                            }
                        }
                        CardControlLive(
                            centrifugoManager = centrifugoManager,
                            openSettings = { showRTMPSettingsDialog = true },
                            rtmpStreamStatus = rtmpStreamStatus,
                            rtmpError = rtmpError,
                            isRtmpLoading = isRtmpLoading,
                            isRtmpStreaming = isRtmpStreaming
                        )
                    }
                }

                // Kanan atas: menu meatball (logout)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(size = 24.dp)
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (livekitShouldConnect && !token.isNullOrEmpty()) successColor else colorPrimary,
                                    shape = RoundedCornerShape(size = 24.dp)
                                )
                                .clickable { showDialogCall = true }
                                .height(48.dp)
                                .width(if (livekitShouldConnect && !token.isNullOrEmpty()) 240.dp else 48.dp),
                            contentAlignment = Alignment.Center
                        ) {

                            Row(modifier = Modifier.padding(horizontal=12.dp), verticalAlignment = Alignment.CenterVertically){
                                Image(
                                    modifier = Modifier
                                        .size(26.dp),
                                    painter = painterResource(id = if (livekitShouldConnect && !token.isNullOrEmpty()) R.drawable.outline_phone_in_talk_24 else R.drawable.outline_call_24),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(if (livekitShouldConnect && !token.isNullOrEmpty()) successColor else colorPrimary)
                                )

                                if (livekitShouldConnect && !token.isNullOrEmpty()){
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = if (speakingNames.isEmpty()) "Tidak ada yang bicara"
                                        else "Speaking: ${speakingNames.joinToString(", ")}",
                                        color = successColor,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        Box(
                            Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(size = 24.dp)
                                )
                                .border(
                                    width = 2.dp,
                                    color = colorPrimary,
                                    shape = RoundedCornerShape(size = 24.dp)
                                )
                                .clickable { recenterTrigger++ }
                                .size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {

                            Icon(
                                painter = painterResource(R.drawable.outline_my_location_24),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = colorPrimary
                            )
                        }

                        Box(
                            Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(size = 24.dp)
                                )
                                .border(
                                    width = 2.dp,
                                    color = colorPrimary,
                                    shape = RoundedCornerShape(size = 24.dp)
                                )
                                .clickable { showMenu = true }
                                .size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {

                            Icon(
                                painter = painterResource(R.drawable.charm_menu_meatball),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = colorPrimary
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF041F44))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(R.drawable.outline_logout_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = dangerColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "Logout", color = Color.White, fontSize = 12.sp)
                                }
                            },
                            onClick = {
                                showMenu = false
                                loggingOut = true
                                authViewModel.logout()
                            }
                        )
                    }
                }

            }

            // ── Dialog pengaturan RTMP ───────────────────────────────────────
            RTMPControl(
                isVisible = showRTMPSettingsDialog,
                onDismiss = { showRTMPSettingsDialog = false },
                onConfirm = { config -> onStartRTMP(config) },
                currentConfig = savedRTMPConfig,
                centrifugoManager = centrifugoManager,
                rtmpStreamStatus = rtmpStreamStatus,
                rtmpError = rtmpError,
                isRtmpLoading = isRtmpLoading,
                isRtmpStreaming = isRtmpStreaming,
                savedRTMPConfig = savedRTMPConfig,
                onStopRTPM = onStopRTMP
            )

            // ── Dialog Mission Brief (rundown dari API) ──────────────────────
            if (showMissionBrief) {
                DialogMissionBrief(
                    onDismiss = { showMissionBrief = false },
                    apiService = livekitApiService,
                )
            }

            // ── LiveKit (audio call) ─────────────────────────────────────────
            val settings = AppSettingsManager.getInstance(context = context)
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (livekitShouldConnect && !token.isNullOrEmpty() && hasAudioPermission) {
                RoomScope(
                    url = settings.getLivekitUrl(),
                    token = token!!,
                    audio = true,
                    video = false,
                    connect = true,
                ) { room ->
                    // 1. Track lokal (mic)
                    val localTrackRefs by rememberTracks(sources = listOf(Track.Source.MICROPHONE))
                    val audioTracks = localTrackRefs.filter {
                        it.publication?.kind == Track.Kind.AUDIO
                    }

                    // 2. Remote audio tracks (sudah subscribed)
                    val remoteAudioTrackRefs by rememberTracks(
                        sources = listOf(Track.Source.MICROPHONE),
                        onlySubscribed = true
                    )
                    val remoteAudioTracks = remoteAudioTrackRefs.filter {
                        it.participant is RemoteParticipant
                    }

                    // ── Effect: siapa yang sedang bicara (active speakers) ────
                    LaunchedEffect(room) {
                        fun names(list: List<io.livekit.android.room.participant.Participant>) =
                            list.map { p ->
                                p.name?.takeIf { it.isNotBlank() }
                                    ?: p.identity?.value
                                    ?: "?"
                            }
                        speakingNames = names(room.activeSpeakers)
                        room.events.collect { event ->
                            if (event is RoomEvent.ActiveSpeakersChanged) {
                                speakingNames = names(event.speakers)
                            }
                        }
                    }
                    DisposableEffect(Unit) {
                        onDispose { speakingNames = emptyList() }
                    }

                    // ── Effect: Mic lokal ─────────────────────────────────────
                    // Pakai localTrackRefs sebagai dependency supaya tunggu mic track ter-publish.
                    LaunchedEffect(localTrackRefs, livekitIsMuted) {
                        localTrackRefs.forEach { trackRef ->
                            val track = trackRef.publication?.track
                            if (track != null) {
                                room.localParticipant.setMicrophoneEnabled(!livekitIsMuted)
                                Log.d("LiveKit", "🎤 Mic enabled = ${!livekitIsMuted}")
                            }
                        }
                    }

                    // ── Effect: Speaker mute via AudioManager (level system) ──
                    LaunchedEffect(livekitIsSpeakerMuted) {
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        am.adjustStreamVolume(
                            AudioManager.STREAM_VOICE_CALL,
                            if (livekitIsSpeakerMuted) AudioManager.ADJUST_MUTE
                            else AudioManager.ADJUST_UNMUTE,
                            0
                        )
                        Log.d("LiveKit", "🔇 STREAM_VOICE_CALL muted=$livekitIsSpeakerMuted")
                    }

                    // ── Effect: Speaker mute via track volume (per-track) ─────
                    for (trackRef in remoteAudioTracks) {
                        val sid = trackRef.publication?.sid ?: continue
                        key(sid) {
                            val track = trackRef.publication?.track
                            LaunchedEffect(livekitIsSpeakerMuted, track) {
                                val audioTrack = track as? RemoteAudioTrack
                                if (audioTrack != null) {
                                    val vol = if (livekitIsSpeakerMuted) 0.0 else 1.0
                                    audioTrack.setVolume(vol)
                                    Log.d("LiveKit", "🔊 Track $sid → volume=$vol")
                                } else {
                                    Log.w("LiveKit", "⚠️ Track $sid bukan RemoteAudioTrack: $track")
                                }
                            }
                        }
                    }

                    if (showDialogCall) {
                        DialogCall(
                            onDismiss = { showDialogCall = false },
                            audioTracks = audioTracks,
                            isMuted = livekitIsMuted,
                            isSpeakerMuted = livekitIsSpeakerMuted,
                            onMuteToggle = onLivekitMuteToggle,
                            onSpeakerToggle = onLivekitSpeakerToggle,
                            onEndCall = {
                                onLivekitDisconnect()
                                livekitViewModel.clearLivekitToken()
                            },
                            onJoin = null
                        )
                    }
                }
            }

            if (showDialogCall && !livekitShouldConnect) {
                DialogCall(
                    onDismiss = { showDialogCall = false },
                    audioTracks = emptyList(),
                    isMuted = livekitIsMuted,
                    isSpeakerMuted = livekitIsSpeakerMuted,
                    onMuteToggle = onLivekitMuteToggle,
                    onSpeakerToggle = onLivekitSpeakerToggle,
                    onEndCall = { },
                    onJoin = { livekitViewModel.fetchLivekitToken() }
                )
            }
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

/**
 * Sisa penyimpanan internal HP (partisi data user) dalam GB, mis. "22.4 GB".
 * Ini volume yang sama tempat rekaman disimpan.
 */
private fun freeStorageText(): String {
    return try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val gb = stat.availableBytes / 1_000_000_000.0
        String.format(Locale.US, "%.1f GB", gb)
    } catch (e: Exception) {
        "-"
    }
}
