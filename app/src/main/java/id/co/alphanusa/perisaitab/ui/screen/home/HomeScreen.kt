package id.co.alphanusa.perisaitab.ui.screen.home

import android.Manifest
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.HazeMaterials
import id.co.alphanusa.perisaitab.R
import id.co.alphanusa.perisaitab.StreamActivity
import id.co.alphanusa.perisaitab.ui.components.backgroundColor
import id.co.alphanusa.perisaitab.ui.components.colorPrimary
import id.co.alphanusa.perisaitab.ui.components.QRCodeScannerDialog
import id.co.alphanusa.perisaitab.data.local.AppSettingsManager
import id.co.alphanusa.perisaitab.data.remote.api.ApiConfig
import id.co.alphanusa.perisaitab.ui.viewmodel.AuthViewModel

@Composable
fun HomeScreen(authViewModel: AuthViewModel = viewModel(), onNavigateToSettings: () -> Unit) {

    val authState by authViewModel.authState.collectAsState()

    var showQRScanner by remember { mutableStateOf(false) }
    var scannedResult by remember { mutableStateOf("") }

    val hazeState = remember { HazeState() }

    var locationStatus by remember { mutableStateOf<String?>(null) }
    var isCheckingLocation by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val camera = permissions[Manifest.permission.CAMERA] ?: false
            val audio = permissions[Manifest.permission.RECORD_AUDIO] ?: false

            if (fineLocation && camera && audio) {
                // Izin diberikan semua, langsung buka StreamActivity
                val intent = Intent(context, StreamActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Izin Kamera, Mic, & Lokasi wajib diberikan!", Toast.LENGTH_LONG).show()
            }
        }
    )

    if (authState.isLoggedIn) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    fun openRCScreen() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                backgroundColor
            )
    )
    {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState)
        )

        Box(
            Modifier
                .width(400.dp)
                .align(Alignment.Center)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .border(
                            width = 0.5.dp,
                            color = colorPrimary,
                            shape = RoundedCornerShape(
                                topStart = 2.dp,
                                topEnd = 2.dp,
                                bottomStart = 10.dp,
                                bottomEnd = 10.dp
                            )
                        )
                        .hazeChild(state = hazeState, style = HazeMaterials.ultraThin())
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(
                            color = Color(0x1A02D8FA),
                            shape = RoundedCornerShape(
                                topStart = 2.dp,
                                topEnd = 2.dp,
                                bottomStart = 10.dp,
                                bottomEnd = 10.dp
                            )
                        )
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)
            )
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = 0.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        color = colorPrimary,
                        shape = RoundedCornerShape(
                            topStart = 2.dp,
                            topEnd = 2.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 0.dp
                        )
                    )

            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .align(Alignment.TopEnd)
            )
            {
                Image(
                    painter = painterResource(id = R.drawable.accent),
                    contentDescription = null,
                    modifier = Modifier
                        .width(32.dp)
                        .height(32.dp)
                )
            }

            Image(
                painter = painterResource(id = R.drawable.border_left),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(28.dp)
                    .height(28.dp)
            )
            Image(
                painter = painterResource(id = R.drawable.border_right),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .width(28.dp)
                    .height(28.dp)
            )
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val homeScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Minimal setinggi viewport → SpaceBetween tetap menyebar saat
                    // muat; kalau konten lebih tinggi dari layar → bisa di-scroll.
                    .heightIn(min = maxHeight)
                    .verticalScroll(homeScrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Header ──────────────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 52.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = colorPrimary,
                                shape = RoundedCornerShape(size = 100.dp)
                            )
                            .width(60.dp)
                            .height(60.dp)
                            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.outline_lock_person_24),
                            contentDescription = null,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colorPrimary),
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "PERISAI",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scan QR Code to connect PERISAI",
                        fontSize = 10.sp,
                        color = Color(0xFFE6FBFF)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Scan QR / Logout
                    Button(
                        onClick = {
                            if (authState.isLoggedIn) {
                                authViewModel.logout()
                            } else {
                                showQRScanner = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when{
                                authState.isLoggedIn -> Color(0xFFB71C1C)
                                authState.isLoading -> Color(0xFFFFA000)
                                else -> colorPrimary
                            },
                            disabledContainerColor = Color(0xFF37474F)
                        )
                    ) {
                        if (authState.isLoading){
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = backgroundColor,
                                strokeWidth = 2.dp
                            )
                        }else{
                            Icon(
                                when{
                                    authState.isLoggedIn ->  Icons.Default.Logout
                                    else -> Icons.Default.QrCodeScanner
                                },
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = backgroundColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(when{
                            authState.isLoggedIn ->  "Logout"
                            authState.isLoading -> "Authenticating..."
                            else -> "Scan QR Code"
                        }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = backgroundColor)
                    }
                }


                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
//                    Row(
//                        verticalAlignment = Alignment.CenterVertically,
//                        modifier = Modifier.clickable(
//                            onClick = { onNavigateToSettings() }
//                        )
//                    ) {
//                        Icon(
//                            Icons.Default.Settings,
//                            contentDescription = null,
//                            tint = colorPrimary,
//                            modifier = Modifier.size(12.dp)
//                        )
//                        Spacer(modifier = Modifier.width(8.dp))
//                        Text("URL Configuration", fontSize = 10.sp, color = colorPrimary)
//
//                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Open Device Management > PoC Radio on PERISAI web app to generate QR Code for each PoC devices",
                        color = Color(0x80E6FBFF),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            }
        }

        // ── QR Scanner Dialog (tidak diubah) ─────────────────────────────────
        if (showQRScanner) {
            QRCodeScannerDialog(
                onDismiss = { showQRScanner = false },
                onQRCodeScanned = { qrCode: String ->
                    val parts = qrCode.split("|")

                    if (parts.size == 5) {
                        val ctx = context ?: return@QRCodeScannerDialog
                        val settings = AppSettingsManager.getInstance(ctx)

                        val url = parts[0]
                        val centrifugo = parts[1]
                        val rtmp = parts[2]
                        val livekit = parts[3]
                        val token = parts[4]

                        settings.setBaseUrl(url)
                        settings.setCentrifugoWebSocketUrl(centrifugo + "/connection/websocket")
                        settings.setRtmpUrl(rtmp)
                        settings.seLivekitUrl(livekit)

                        ApiConfig.getInstance(ctx).recreate(ctx)

                        authViewModel.loginWithQR(token)

                        showQRScanner = false
                    } else {
                        Toast.makeText(context, "Format QR tidak valid!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        if (scannedResult.isNotEmpty()) {
            Log.d("QR CODE SCAN", "Scanned Result: $scannedResult")

        }
    }
}
