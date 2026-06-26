package id.co.alphanusa.perisaitab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.co.alphanusa.perisaitab.realtime.CentrifugoClientManager
import id.co.alphanusa.perisaitab.realtime.CentrifugoConnectionState

@Composable
fun CardControlLive(
    modifier: Modifier = Modifier,
    centrifugoManager: CentrifugoClientManager,
    backgroundColor: Color = Color.Black.copy(alpha = 0.4f),
    borderWidth: Dp = 4.dp,
    borderColor: Color = colorPrimary,
    openSettings: () -> Unit,
    rtmpStreamStatus: RtmpStreamStatus? = null,
    rtmpError: String? = null,
    isRtmpLoading: Boolean = false,
    isRtmpStreaming: Boolean = false,
) {
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
        isLive ->  "Video Live"
        isRtmpLoading -> "Video Live Connecting..."
        rtmpError != null -> "Video Live Disconnected"
        else -> "Video Live Disconnected"
    }


    TacticalContainer(
        modifier = modifier.height(74.dp),
        accentColor = borderColor,
        background = backgroundColor,
        borderWidth = borderWidth,
        onClick = { openSettings() }
    ) {
        // Isi utama
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.Start,
        ) {

            Box(
                modifier = modifier
                    .wrapContentHeight()
                    .clip(shape = RoundedCornerShape(size = 2.dp))
                    .padding( 4.dp),

                ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        8.dp, Alignment.CenterHorizontally
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(8.dp)
                            .background(
                                color = colorsLiveConnect,
                                shape = RoundedCornerShape(size = 8.dp)
                            )
                            .padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
                    )
                    Text(
                        text = textLiveConnect,
                        color = colorsLiveConnect,
                        letterSpacing = 2.sp,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(1.dp))

            Box(
                modifier = modifier
                    .wrapContentHeight()
                    .clip(shape = RoundedCornerShape(size = 2.dp))
                    .padding( 4.dp),

                ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        8.dp, Alignment.CenterHorizontally
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(8.dp)
                            .background(
                                color = colorCentrifugo as Color,
                                shape = RoundedCornerShape(size = 8.dp)
                            )
                            .padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
                    )
                    Text(
                        text = textCentrifugoStatus.toString(),
                        color = colorCentrifugo as Color,
                        letterSpacing = 2.sp,
                        fontSize = 10.sp
                    )
                }
            }

        }
    }
}
