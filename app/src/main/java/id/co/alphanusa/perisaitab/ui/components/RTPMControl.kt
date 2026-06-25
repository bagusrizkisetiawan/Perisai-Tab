package id.co.alphanusa.perisaitab.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dji.v5.manager.datacenter.livestream.LiveStreamStatus
import dji.v5.manager.datacenter.livestream.LiveVideoBitrateMode
import dji.v5.manager.datacenter.livestream.StreamQuality
import dji.v5.manager.interfaces.ICameraStreamManager
import id.co.alphanusa.perisaitab.realtime.CentrifugoClientManager
import id.co.alphanusa.perisaitab.realtime.CentrifugoConnectionState
import id.co.tigabersama.surveillance.data.manager.CentrifugoClientManager
import id.co.tigabersama.surveillance.data.manager.CentrifugoConnectionState
import id.co.tigabersama.surveillance.ui.theme.colorPrimary
import id.co.tigabersama.surveillance.ui.theme.dangerColor
import id.co.tigabersama.surveillance.ui.theme.successColor
import id.co.tigabersama.surveillance.ui.theme.warningColor
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RTMPControl(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (RTMPConfig) -> Unit,
    currentConfig: RTMPConfig = RTMPConfig(),
    backgroundColor: Color = Color.Black.copy(alpha = 0.4f),
    borderWidth: Dp = 4.dp,
    borderColor: Color = colorPrimary,
    centrifugoManager: CentrifugoClientManager,
    rtmpStreamStatus: LiveStreamStatus?,
    rtmpError: String?,
    isRtmpLoading: Boolean,
    isRtmpStreaming: Boolean = false,
    savedRTMPConfig: RTMPConfig,
    onStopRTPM: () -> Unit,
) {
    var qualityExpanded by remember { mutableStateOf(false) }
    var bitrateExpanded by remember { mutableStateOf(false) }
    val lastDataSent by centrifugoManager.lastDataSent.collectAsState()

    val centrifugoState by centrifugoManager.connectionState.collectAsState()

    val isLive = (isRtmpStreaming || rtmpStreamStatus?.isStreaming == true) && rtmpError == null


    val colorCentrifugo = when (centrifugoState) {
        CentrifugoConnectionState.CONNECTED -> {
            successColor
        }

        CentrifugoConnectionState.CONNECTING -> {
            warningColor
        }

        CentrifugoConnectionState.ERROR, CentrifugoConnectionState.DISCONNECTED -> {
            dangerColor
        }

        else -> {}
    }
    val textCentrifugoStatus = when (centrifugoState) {
        CentrifugoConnectionState.CONNECTED -> {
            "Telemetry Live"
        }

        CentrifugoConnectionState.CONNECTING -> {
            "Telemetry Live Connecting..."
        }

        CentrifugoConnectionState.ERROR, CentrifugoConnectionState.DISCONNECTED -> {
            "Telemetry Live Disconnected"
        }

        else -> {}
    }


    val colorsLiveConnect = when {
        isLive -> successColor
        isRtmpLoading -> warningColor
        rtmpError != null -> dangerColor
        else -> dangerColor
    }



    val textLiveConnect = when {
        isLive ->  "Video Live "
        isRtmpLoading -> "Video Live Connecting..."
        rtmpError != null -> "Video Live Disconnected"
        else -> "Video Live Disconnected"
    }

    if (isVisible) {
        var config by remember { mutableStateOf(currentConfig) }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false   // WAJIB untuk lebar custom
            )
        ) {

            // Density hack untuk mengecilkan padding OutlinedTextField
            val smallerDensity = object : Density {
                override val density = LocalDensity.current.density
                override val fontScale = 0.75f
            }

            CompositionLocalProvider(LocalDensity provides smallerDensity) {

                TacticalContainer(
                    modifier = Modifier
                        .height(280.dp)
                        .width(340.dp),
                    accentColor = borderColor,
                    background = backgroundColor,
                    borderWidth = borderWidth
                ) {

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(2.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {

                            Column {

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(
                                        12.dp, Alignment.CenterHorizontally
                                    ),
                                    verticalAlignment = Alignment.Top
                                ) {

                                    Column(modifier = Modifier.padding(top = 4.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(colorsLiveConnect, RoundedCornerShape(8.dp))
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                                        Text(
                                            text = textLiveConnect,
                                            color = colorsLiveConnect,
                                            letterSpacing = 2.sp,
                                            fontSize = 18.sp
                                        )

                                        // Jika LIVE → tampilkan info LIVE
                                        if (isLive) {
                                            rtmpStreamStatus?.let { status ->
                                                Row {
                                                    Text("Resolution", Modifier.width(80.dp), color = Color.White, fontSize = 14.sp)
                                                    Text(":", color = Color.White, fontSize = 14.sp)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("${status.resolution?.width}x${status.resolution?.height}", color = Color.White, fontSize = 14.sp)
                                                }

                                                Row {
                                                    Text("FPS", Modifier.width(80.dp), color = Color.White, fontSize = 14.sp)
                                                    Text(":", color = Color.White, fontSize = 14.sp)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("${status.fps}", color = Color.White, fontSize = 14.sp)
                                                }

                                                Row {
                                                    Text("BITRATE", Modifier.width(80.dp), color = Color.White, fontSize = 14.sp)
                                                    Text(":", color = Color.White, fontSize = 14.sp)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("${status.vbps}", color = Color.White, fontSize = 14.sp)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        onStopRTPM()
                                                        onDismiss()
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFD64545)),   // warna border
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = Color.Transparent,     // OutlinedButton default transparan
                                                        contentColor = Color(0xFFD64545)             // warna teks
                                                    )
                                                ) {
                                                    Text(
                                                        text = "Stop Stream",
                                                        fontSize = 14.sp,
                                                        color = Color(0xFFD64545)
                                                    )
                                                }
                                            }
                                        }
                                        else if (isRtmpLoading) {
                                            // ✅ TAMBAHAN: Status connecting dengan tombol cancel
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(bottom = 4.dp).fillMaxWidth()
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = colorPrimary
                                                )

                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    onStopRTPM()
                                                    onDismiss()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, Color.White),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = Color.Transparent,
                                                    contentColor = Color.White
                                                )
                                            ) {
                                                Text(
                                                    text = "Cancel",
                                                    fontSize = 14.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        else {
                                            Button(
                                                onClick = {
                                                    onConfirm(config)
                                                    onDismiss()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = borderColor),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Start Stream",
                                                    fontSize = 14.sp,
                                                    color = Color.Black
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // ===========================
                        // Telemetry Live
                        // ===========================
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clip(shape = RoundedCornerShape(size = 2.dp))
                            ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(
                                    12.dp, Alignment.CenterHorizontally
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .height(8.dp)
                                            .background(
                                                color = colorCentrifugo as Color,
                                                shape = RoundedCornerShape(size = 8.dp)
                                            )
                                            .padding(
                                                start = 4.dp,
                                                top = 4.dp,
                                                end = 4.dp,
                                                bottom = 4.dp
                                            )
                                    )
                                }
                                Column {
                                    Text(
                                        text = textCentrifugoStatus.toString(),
                                        color = colorCentrifugo as Color,
                                        letterSpacing = 2.sp,
                                        fontSize = 18.sp
                                    )
                                    lastDataSent?.let { data ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Last sent: ${
                                                SimpleDateFormat("HH:mm:ss.SSS").format(
                                                    Date(
                                                        data.timestamp
                                                    )
                                                )
                                            }",
                                            color = Color.White,
                                            letterSpacing = 2.sp,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}