package id.co.alphanusa.perisaitab.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.co.alphanusa.perisaitab.R
import id.co.alphanusa.perisaitab.ui.components.colorPrimary
import org.osmdroid.util.GeoPoint

@Composable
fun DialogMap(
    onDismiss: () -> Unit,
    deviceLocation: GeoPoint = GeoPoint(-6.9828, 110.4091),
    deviceMarkerIcon: Int? = null,
    pocYaw: Float? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
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
                        .fillMaxWidth()
                        .matchParentSize()
                        .background(
                            color = Color(0x1A02D8FA),
                            shape = RoundedCornerShape(
                                topStart = 2.dp,
                                topEnd = 2.dp,
                                bottomStart = 10.dp,
                                bottomEnd = 10.dp
                            )
                        )
                        .padding(20.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        color = colorPrimary,
                        shape = RoundedCornerShape(
                            topStart = 2.dp,
                            topEnd = 2.dp
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tactical Map",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Image(
                        modifier = Modifier
                            .clickable {
                                onDismiss()
                            }
                            .size(24.dp),
                        painter = painterResource(id = R.drawable.outline_close_24),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                }
            }

            Image(
                painter = painterResource(id = R.drawable.border_left),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(28.dp)
            )

            Image(
                painter = painterResource(id = R.drawable.border_right),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp).padding(top = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                OsmdroidMapView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    deviceLocation = deviceLocation,
                    deviceMarkerIcon = deviceMarkerIcon,
                    pocYaw = pocYaw
                )
            }
        }
    }
}