package id.co.alphanusa.perisaitab

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbManager
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
import androidx.compose.runtime.rememberCoroutineScope
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
import id.co.alphanusa.perisaitab.ui.components.TacticalContainer
import id.co.alphanusa.perisaitab.ui.components.VlcVideoView
import id.co.alphanusa.perisaitab.ui.components.backgroundColor
import id.co.alphanusa.perisaitab.ui.theme.PERISAITABTheme
import id.co.alphanusa.perisaitab.ui.viewmodel.LivekitViewModel
import id.co.alphanusa.perisaitab.ui.viewmodel.LivekitViewModelFactory
import id.co.alphanusa.perisaitab.ui.viewmodel.UserViewModel
import id.co.alphanusa.perisaitab.ui.viewmodel.UserViewModelFactory
import id.co.alphanusa.perisaitab.utils.GoProUsbCamera
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

/** Status koneksi yang ditampilkan ke pengguna. */
private sealed interface UiState {
    data object Disconnected : UiState
    data object Connecting : UiState
    data class Connected(val result: GoProUsbCamera.Result.Connected) : UiState
    data class Error(val message: String) : UiState
}

class StreamActivity : ComponentActivity(), ConnectChecker {
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

                                yaw = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                                pitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
                                roll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()

                                sendToCentrifugo()
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

        val scope = rememberCoroutineScope()
        val camera = remember { GoProUsbCamera(context) }

        var state by remember { mutableStateOf<UiState>(UiState.Disconnected) }

        // ── State RTMP (relay GoPro → server RTMP) ───────────────────────────
        var savedRTMPConfig by remember { mutableStateOf(RTMPConfig()) }
        var rtmpStreamStatus by remember { mutableStateOf<RtmpStreamStatus?>(null) }
        var rtmpError by remember { mutableStateOf<String?>(null) }
        var isRtmpLoading by remember { mutableStateOf(false) }
        var isRtmpStreaming by remember { mutableStateOf(false) }
        // Target RTMP aktif. null = hanya preview (belum/berhenti relay).
        var rtmpRestreamUrl by remember { mutableStateOf<String?>(null) }

        fun connect() {
            state = UiState.Connecting
            scope.launch {
                state = when (val r = camera.connect()) {
                    is GoProUsbCamera.Result.Connected -> UiState.Connected(r)
                    is GoProUsbCamera.Result.Failed -> UiState.Error(r.reason)
                }
            }
        }

        fun onSaveRTMPConfig(config: RTMPConfig) {
            savedRTMPConfig = config
        }

        fun onStartRTMP(config: RTMPConfig) {
            val target = this@StreamActivity.rtmpUrl
            if (target.isNullOrEmpty()) {
                rtmpError = "URL RTMP server belum siap, coba lagi sebentar."
                return
            }
            if (state !is UiState.Connected) {
                rtmpError = "GoPro belum tersambung."
                return
            }
            savedRTMPConfig = config
            rtmpError = null
            isRtmpLoading = true
            isRtmpStreaming = false
            // Memicu VlcVideoView dibuat ulang dengan sout RTMP aktif.
            rtmpRestreamUrl = target
        }

        val onStopRTMP: () -> Unit = {
            rtmpRestreamUrl = null
            isRtmpStreaming = false
            isRtmpLoading = false
            rtmpStreamStatus = null
        }

        // Coba sambung saat layar dibuka.
        LaunchedEffect(Unit) { connect() }

        // Dengarkan colok/cabut USB → otomatis sambung / putus.
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    when (intent?.action) {
                        UsbManager.ACTION_USB_DEVICE_ATTACHED -> connect()
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                            scope.launch { camera.disconnect() }
                            state = UiState.Disconnected
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            ContextCompat_registerReceiver(context, receiver, filter)
            onDispose {
                runCatching { context.unregisterReceiver(receiver) }
                scope.launch { camera.disconnect() }
            }
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
                    if (splitMapToCamera){
                        Modifier
                            .width(300.dp)
                            .height(200.dp)
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .zIndex(0.2f)
                    }else{
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .zIndex(0.1f)
                    }
                ,
            ) {
                OsmdroidMapView(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .zIndex(if (splitMapToCamera)1f else 2f),
                    deviceLocation = GeoPoint(
                        location?.latitude ?: -6.9828,
                        location?.longitude ?: 110.4091
                    ),
                    deviceMarkerIcon = R.drawable.ic_map,
                    pocYaw = yaw
                )
                Box(
                    modifier = Modifier.fillMaxSize().clickable{splitMapToCamera = !splitMapToCamera}.zIndex(if (splitMapToCamera)2f else 1f)
                )
            }


            Box(
                modifier = if (!splitMapToCamera){
                    Modifier
                        .width(300.dp)
                        .height(200.dp)
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .zIndex(0.2f)
                }else{
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .zIndex(0.1f)
                }
            ){
                when (val s = state) {
                    is UiState.Connected -> {
                        VlcVideoView(
                            streamUrl = s.result.streamUrl,
                            rtmpUrl = rtmpRestreamUrl,
                            onPlaying = {
                                if (rtmpRestreamUrl != null) {
                                    isRtmpLoading = false
                                    isRtmpStreaming = true
                                    rtmpStreamStatus = RtmpStreamStatus(
                                        isStreaming = true,
                                        resolution = RtmpResolution(
                                            savedRTMPConfig.resolutionWidth,
                                            savedRTMPConfig.resolutionHeight
                                        ),
                                        fps = 30,
                                        vbps = savedRTMPConfig.bitrateKbps,
                                    )
                                }
                            },
                            onError = {
                                if (rtmpRestreamUrl != null) {
                                    rtmpError = "Gagal mengirim ke RTMP."
                                    isRtmpLoading = false
                                    isRtmpStreaming = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .zIndex(if (splitMapToCamera) 2f else 1f),
                        )
                        Text(
                            text = "Stream: ${s.result.streamUrl}  •  GoPro ${s.result.goProIp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                    }

                    is UiState.Connecting -> VideoPlaceholder {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(12.dp))
                            Text("Menyambung ke GoPro…", color = Color.White)
                        }
                    }

                    is UiState.Error -> VideoPlaceholder {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Gagal menyambung", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                s.message,
                                color = Color(0xFFFFCDD2),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    is UiState.Disconnected -> VideoPlaceholder {
                        Text("Colok GoPro via USB", color = Color.White)
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize().clickable{splitMapToCamera = !splitMapToCamera}.zIndex(if (splitMapToCamera)1f else 2f)
                )
            }


            Box(
                modifier = Modifier.fillMaxSize().zIndex(0.3f)
            ){
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ){
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



//            Column(
//                verticalArrangement = Arrangement.SpaceBetween,
//                modifier = Modifier
//                    .align(Alignment.TopCenter)
//                    .fillMaxWidth()
//            ) {
//                ConnectionStatusBar(
//                    username = user?.Name?.trim(),
//                    connectionState = connectionState,
//                    onLogoutClick = {
//                        val intent = Intent(context, MainActivity::class.java)
//                        intent.flags =
//                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//                        context.startActivity(intent)
//                    }
//                )
//
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(16.dp),
//                    horizontalArrangement = Arrangement.SpaceBetween,
//                ) {
//                    Box(
//                        modifier = Modifier
//                            .width(100.dp)
//                            .height(100.dp),
//                    ) {
//                        if (!showDialogMap){
//                            OsmdroidMapView(
//                                modifier = Modifier
//                                    .fillMaxHeight()
//                                    .fillMaxWidth(),
//                                deviceLocation = GeoPoint(
//                                    location?.latitude ?: -6.9828,
//                                    location?.longitude ?: 110.4091
//                                ),
//                                deviceMarkerIcon = R.drawable.ic_map,
//                                pocYaw = yaw
//                            )
//                        }
//                        Box(
//                            modifier = Modifier
//                                .fillMaxSize()
//                                .clickable { showDialogMap = true }
//                        )
//                    }
//                    if (isStreaming) {
//                        AlertStream()
//                    }
//                }
//            }
//
//            // ── Bottom Bar: Lokasi + Tombol ─────────────────────────────────
//            Column(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .fillMaxWidth()
//                    .hazeChild(state = hazeState, style = HazeMaterials.ultraThin())
//                    .background(color = Color(0x80070C28))
//                    .padding(horizontal = 16.dp, vertical = 24.dp),
//                verticalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.SpaceBetween,
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    // Tombol Stream
//
//                    if (livekitShouldConnect && !token.isNullOrEmpty()) {
//                        Button(
//                            onClick = onStreamClick,
//                            enabled = hasPermissions,
//                            modifier = Modifier
//                                .width(44.dp)
//                                .height(40.dp),
//                            shape = RoundedCornerShape(2.dp),
//                            contentPadding = PaddingValues(0.dp),
//                            colors = ButtonDefaults.buttonColors(
//                                containerColor = if (isStreaming) dangerColor else colorPrimary,
//                                disabledContainerColor = Color.Gray
//                            )
//                        ) {
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                verticalAlignment = Alignment.CenterVertically,
//                                horizontalArrangement = Arrangement.Center
//                            ) {
//                                Image(
//                                    painter = painterResource(id = if (isStreaming) R.drawable.outline_stop_circle_24 else R.drawable.outline_smart_display_24),
//                                    contentDescription = null,
//                                    colorFilter = ColorFilter.tint(if (isStreaming) Color.White else backgroundColor)
//                                )
//                            }
//
//                        }
//                        Spacer(modifier = Modifier.width(8.dp))
//                        Box(
//                            Modifier
//                                .clickable {
//                                    showStopStreamDialog = true
//                                }
//                                .border(
//                                    width = 1.dp,
//                                    color = if (livekitShouldConnect && !token.isNullOrEmpty()) successColor else colorPrimary,
//                                    shape = RoundedCornerShape(size = 2.dp)
//                                )
//                                .weight(1f)
//                                .height(40.dp)
//                                .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
//                        ) {
//                            Row(
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    .fillMaxHeight(),
//                                horizontalArrangement = Arrangement.Start,
//                                verticalAlignment = Alignment.CenterVertically,
//                            ) {
//                                Image(
//                                    painter = painterResource(id = if (livekitShouldConnect && !token.isNullOrEmpty()) R.drawable.outline_phone_in_talk_24 else R.drawable.outline_call_24),
//                                    contentDescription = null,
//                                    colorFilter = ColorFilter.tint(if (livekitShouldConnect && !token.isNullOrEmpty()) successColor else colorPrimary)
//                                )
//                                Spacer(modifier = Modifier.width(8.dp))
//                                if (listUserSpeaking.isNotEmpty()) {
//                                    Text(
//                                        text = "Speaking: " + listUserSpeaking.joinToString(", "),
//                                        color = successColor,
//                                        fontSize = 10.sp,
//                                        maxLines = 1,
//                                        overflow = TextOverflow.Ellipsis
//                                    )
//                                } else {
//                                    Text(
//                                        text = "Speaking: -",
//                                        color = successColor,
//                                        fontSize = 10.sp,
//                                        maxLines = 1,
//                                        overflow = TextOverflow.Ellipsis
//                                    )
//                                }
//                            }
//                        }
//                    } else {
//                        Button(
//                            onClick = onStreamClick,
//                            enabled = hasPermissions,
//                            modifier = Modifier
//                                .weight(1f)
//                                .height(40.dp),
//                            shape = RoundedCornerShape(2.dp),
//                            colors = ButtonDefaults.buttonColors(
//                                containerColor = if (isStreaming) dangerColor else colorPrimary,
//                                disabledContainerColor = Color.Gray
//                            )
//                        ) {
//                            Row(
//                                verticalAlignment = Alignment.CenterVertically,
//                                horizontalArrangement = Arrangement.spacedBy(8.dp)
//                            ) {
//                                Image(
//                                    painter = painterResource(id = if (isStreaming) R.drawable.outline_stop_circle_24 else R.drawable.outline_smart_display_24),
//                                    contentDescription = null,
//                                    colorFilter = ColorFilter.tint(if (isStreaming) Color.White else backgroundColor)
//                                )
//                                Text(
//                                    text = if (isStreaming) "Stop Stream" else "Start Stream",
//                                    color = if (isStreaming) Color.White else backgroundColor,
//                                    fontSize = 12.sp,
//                                    fontWeight = FontWeight.Bold
//                                )
//                            }
//
//                        }
//                        Spacer(modifier = Modifier.width(8.dp))
//                        Box(
//                            Modifier
//                                .clickable {
//                                    showStopStreamDialog = true
//                                }
//                                .border(
//                                    width = 1.dp,
//                                    color = colorPrimary,
//                                    shape = RoundedCornerShape(size = 2.dp)
//                                )
//                                .width(44.dp)
//                                .height(40.dp)
//                                .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
//                        ) {
//                            Image(
//                                modifier = Modifier.align(Alignment.Center),
//                                painter = painterResource(id = R.drawable.outline_call_24),
//                                contentDescription = null,
//                                colorFilter = ColorFilter.tint(colorPrimary)
//                            )
//                        }
//                    }
//
//                    Spacer(modifier = Modifier.width(8.dp))
//
//                    Box(
//                        Modifier
//                            .clickable {
//                                onSwitchCamera()
//                            }
//                            .border(
//                                width = 1.dp,
//                                color = colorPrimary,
//                                shape = RoundedCornerShape(size = 2.dp)
//                            )
//                            .width(44.dp)
//                            .height(40.dp)
//                            .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
//                    ) {
//                        Image(
//                            modifier = Modifier.align(Alignment.Center),
//                            painter = painterResource(id = R.drawable.outline_flip_camera_ios_24),
//                            contentDescription = null,
//                            colorFilter = ColorFilter.tint(colorPrimary)
//                        )
//                    }
//                }
//            }

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
            val settings = AppSettingsManager.getInstance(context)




            if (livekitShouldConnect && !token.isNullOrEmpty()) {
                RoomScope(
                    url = settings.getLivekitUrl(),
                    token = token!!,
                    audio = true,
                    video = false,
                    connect = true,
                ) {

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

                    // ── Effect: Mic lokal ──────────────────────────────────────────────────
                    // ✅ Pakai localTrackRefs sebagai dependency supaya tunggu mic track ter-publish
                    LaunchedEffect(localTrackRefs, livekitIsMuted) {
                        localTrackRefs.forEach { trackRef ->
                            val track = trackRef.publication?.track
                            if (track != null) {
                                it.localParticipant.setMicrophoneEnabled(!livekitIsMuted)
                                Log.d("LiveKit", "🎤 Mic enabled = ${!livekitIsMuted}")
                            }
                        }
                    }

                    // ── Effect: Speaker mute via AudioManager (level system) ───────────────
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

                    // ── Effect: Speaker mute via track volume (level LiveKit, per-track) ───
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
                    // Dialog hanya terima data, tidak kelola koneksi
                    if (showStopStreamDialog) {
                        DialogCall(
                            onDismiss = { showStopStreamDialog = false },
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

            if (showStopStreamDialog && !livekitShouldConnect) {
                DialogCall(
                    onDismiss = { showStopStreamDialog = false },
                    audioTracks = emptyList(),
                    isMuted = livekitIsMuted,
                    isSpeakerMuted = livekitIsSpeakerMuted,     // ← fix: tambah yang hilang
                    onMuteToggle = onLivekitMuteToggle,
                    onSpeakerToggle = onLivekitSpeakerToggle,   // ← fix: tambah yang hilang
                    onEndCall = { },
                    onJoin = { livekitViewModel.fetchLivekitToken() }
                )
            }
            if (showDialogMap) {
                DialogMap(
                    onDismiss = { showDialogMap = false }, deviceLocation = GeoPoint(
                        location?.latitude ?: -6.9828,
                        location?.longitude ?: 110.4091
                    ),
                    deviceMarkerIcon = R.drawable.ic_map,
                    pocYaw = yaw
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
