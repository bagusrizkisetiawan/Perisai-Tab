package id.co.alphanusa.perisaitab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import id.co.alphanusa.perisaitab.ui.components.backgroudOverlay
import id.co.alphanusa.perisaitab.ui.components.bgDangerColor
import id.co.alphanusa.perisaitab.ui.components.bgSuccessColor
import id.co.alphanusa.perisaitab.ui.components.bgWarningColor
import id.co.alphanusa.perisaitab.ui.components.dangerColor
import id.co.alphanusa.perisaitab.ui.components.successColor
import id.co.alphanusa.perisaitab.ui.components.warningColor
import id.co.alphanusa.perisaitab.ui.viewmodel.AuthViewModel
import id.co.alphanusa.perisaitab.realtime.CentrifugoConnectionState

@Composable
fun ConnectionStatusBar(
    username: String? = null,
    connectionState: CentrifugoConnectionState,
    onLogoutClick: () -> Unit
) {
    val authViewModel: AuthViewModel = viewModel()

    val isConnected = connectionState == CentrifugoConnectionState.CONNECTED
    val isConnecting = connectionState == CentrifugoConnectionState.CONNECTING


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroudOverlay)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 8.dp)
            .statusBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            username?.let {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                    text = it,
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Start,
                    maxLines = 1
                )
            }
            // Status Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            isConnected -> bgSuccessColor
                            isConnecting -> bgWarningColor
                            else -> bgDangerColor
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isConnected -> successColor
                                isConnecting -> warningColor
                                else -> dangerColor
                            }
                        )
                )
                Text(
                    text = when {
                        isConnected -> "Connected to PERISAI Server"
                        isConnecting -> "Connecting to PERISAI Server..."
                        else -> "Disconnected from PERISAI Server"
                    },
                    color = when {
                        isConnected -> successColor
                        isConnecting -> warningColor
                        else -> dangerColor
                    },
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        MenuWithLogout(
            onLogoutClick = {
                authViewModel.logout()
                onLogoutClick()
            }
        )
    }
}


@Composable
fun MenuWithLogout(
    onLogoutClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Menu",
                tint = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Logout") },
                onClick = {
                    expanded = false
                    onLogoutClick()
                }
            )
        }
    }
}