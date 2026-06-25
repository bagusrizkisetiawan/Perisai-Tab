package id.co.alphanusa.perisaitab.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.HazeMaterials
import id.co.alphanusa.perisaitab.R
import id.co.alphanusa.perisaitab.data.remote.api.ApiConfig
import id.co.alphanusa.perisaitab.ui.viewmodel.LivekitViewModel
import id.co.alphanusa.perisaitab.ui.viewmodel.LivekitViewModelFactory
import id.co.alphanusa.perisaitab.data.remote.response.Participant
import id.co.alphanusa.perisaitab.ui.components.backgroundColor
import id.co.alphanusa.perisaitab.ui.components.colorPrimary
import id.co.alphanusa.perisaitab.ui.components.dangerColor
import id.co.alphanusa.perisaitab.ui.components.successColor
import io.livekit.android.compose.types.TrackReference
import io.livekit.android.events.ParticipantEvent
import io.livekit.android.events.collect
import io.livekit.android.room.track.Track

@Composable
fun DialogCall(
    onDismiss: () -> Unit,
    audioTracks: List<TrackReference>,
    isMuted: Boolean,
    isSpeakerMuted: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onEndCall: () -> Unit,
    onJoin: (() -> Unit)? = null,
) {
    val hazeState = remember { HazeState() }
    val isConnected = onJoin == null
    val authManager = ApiConfig.getInstance(context = LocalContext.current)
    val livekitApiService = authManager.apiService
    val factory = remember(livekitApiService) { LivekitViewModelFactory(livekitApiService) }
    val livekitViewModel: LivekitViewModel = viewModel(factory = factory)
    val participants by livekitViewModel.listParticipant.collectAsState()

    LaunchedEffect(Unit) {
        livekitViewModel.fetchListParticipant()
    }

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
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp)
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
                        text = "Tactical Communication",
                        fontSize = 10.sp,
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
                    .padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (isConnected) {
                    Spacer(modifier = Modifier.height(36.dp))

                    ParticipantsGrid(
                        audioTracks = audioTracks,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                    )

                    if (audioTracks.isEmpty()) {
                        Text("Menunggu peserta lain...", color = Color.Gray, fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // BUTTON JOIN
                if (!isConnected) {

                    if (participants.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.outline_group_off_24),
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(60.dp),
                                colorFilter = ColorFilter.tint(colorPrimary)
                            )
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = "No one else in Tactical Communication Room",
                                color = Color.White,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = "Join Tactical Communication to interact with participants",
                                color = Color.White,
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center

                            )
                        }
                    }else{
                        ParticipantsJoined(participants = participants)
                    }


                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {

                        Box(
                            Modifier
                                .clickable {
                                    onSpeakerToggle()
                                }
                                .border(
                                    width = 1.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .background(
                                    if (isSpeakerMuted) Color.Transparent else Color.White,
                                    RoundedCornerShape(4.dp)
                                )
                                .width(42.dp)
                                .height(42.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {

                            Image(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Center),
                                painter = painterResource(
                                    id = if (isSpeakerMuted) R.drawable.outline_volume_off_24
                                    else R.drawable.outline_volume_up_24
                                ),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(if (isSpeakerMuted) Color.White else Color.Black)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .clickable {
                                        onMuteToggle()
                                    }
                                    .border(
                                        width = 1.dp,
                                        color = Color.White,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .background(
                                        if (isMuted) Color.Transparent else Color.White,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .width(42.dp)
                                    .height(42.dp)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {

                                Image(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.Center),
                                    painter = painterResource(
                                        id = if (isMuted) R.drawable.outline_mic_off_24
                                        else R.drawable.outline_mic_24
                                    ),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(if (isMuted) Color.White else Color.Black)
                                )
                            }
//                            Spacer(modifier = Modifier.height(6.dp))
//                            Text(
//                                text = if (isMuted) "Mic Off" else "Mic On",
//                                color = Color.White,
//                                fontSize = 10.sp
//                            )
                        }


                    }


                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        Modifier
                            .clickable {
                                onJoin?.invoke()
                            }
                            .background(colorPrimary, RoundedCornerShape(2.dp))
                            .fillMaxWidth()
                            .height(36.dp)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                modifier = Modifier.size(12.dp),
                                painter = painterResource(id = R.drawable.outline_call_24),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(backgroundColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Join Tactical Communication",
                                fontSize = 10.sp,
                                color = backgroundColor,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .clickable {
                                    onSpeakerToggle()
                                }
                                .border(
                                    width = 1.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .background(
                                    if (isSpeakerMuted) Color.Transparent else Color.White,
                                    RoundedCornerShape(4.dp)
                                )
                                .width(42.dp)
                                .height(42.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {

                            Image(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Center),
                                painter = painterResource(
                                    id = if (isSpeakerMuted) R.drawable.outline_volume_off_24
                                    else R.drawable.outline_volume_up_24
                                ),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(if (isSpeakerMuted) Color.White else Color.Black)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clickable {
                                    onMuteToggle()
                                }
                                .border(
                                    width = 1.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .background(
                                    if (isMuted) Color.Transparent else Color.White,
                                    RoundedCornerShape(4.dp)
                                )
                                .width(42.dp)
                                .height(42.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {

                            Image(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Center),
                                painter = painterResource(
                                    id = if (isMuted) R.drawable.outline_mic_off_24
                                    else R.drawable.outline_mic_24
                                ),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(if (isMuted) Color.White else Color.Black)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clickable {
                                    onEndCall()
                                }
                                .background(dangerColor, RoundedCornerShape(4.dp))
                                .width(42.dp)
                                .height(42.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {

                            Image(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Center),
                                painter = painterResource(id = R.drawable.outline_call_end_24),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ParticipantsJoined(participants: List<Participant>) {

    val maxVisible = 2
    val visibleParticipants = participants.take(maxVisible)
    val remainingCount = participants.size - maxVisible

    Column(modifier = Modifier.padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {

        // === AVATAR STACK ===
        Box(
            modifier = Modifier.height(40.dp),
            contentAlignment = Alignment.CenterStart
        ) {

            Row {
                visibleParticipants.forEachIndexed { index, participant ->
                    Box(
                        modifier = Modifier
                            .offset(x = (index * (-12)).dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(backgroundColor)
                            .border(2.dp, colorPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = participant.name?.take(1)?.uppercase() ?: "?"
                        Text(
                            text = initial,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }

                // === +X CIRCLE ===
                if (remainingCount > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = (visibleParticipants.size * (-12)).dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E3A5F))
                            .border(2.dp, colorPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+$remainingCount",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // === TEXT ===
        Text(
            text = buildJoinedText(participants),
            color = Color.White,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )
    }
}

fun buildJoinedText(participants: List<Participant>): String {
    return when {
        participants.isEmpty() -> "No one joined"
        participants.size == 1 -> "${participants[0].name} joined"
        participants.size == 2 -> "${participants[0].name}, ${participants[1].name} joined"
        else -> {
            val first = participants[0].name
            val second = participants[1].name
            val remaining = participants.size - 2
            "$first, $second, and $remaining other joined"
        }
    }
}


@Composable
fun ParticipantsGrid(
    audioTracks: List<TrackReference>,
    modifier: Modifier = Modifier
) {
    if (audioTracks.isEmpty()) return

    val rows = remember(audioTracks) { chunkedRows(audioTracks) }
    val maxCols = rows.maxOfOrNull { it.size } ?: 1
    val gap = 8.dp

    // 🔹 Tinggi per item: 1 baris = 360.dp, 2+ baris = 180.dp
    val itemHeight = if (rows.size <= 1) 240.dp else 116.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val itemWidth = (maxWidth - gap * (maxCols - 1)) / maxCols

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),   // ✅ scrollable
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
                ) {
                    rowItems.forEach { trackRef ->
                        Box(
                            modifier = Modifier
                                .width(itemWidth)
                                .height(itemHeight)        // ✅ tinggi konsisten
                        ) {
                            ParticipantBox(
                                trackRef = trackRef,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
/**
 * Aturan pembagian baris:
 * - 1 → [1]
 * - 2 → [2]
 * - 3 → [2, 1]   (kanan-kiri, bawah)
 * - 4 → [2, 2]
 * - 5+ → kelipatan 3 (max 3 per baris)
 */
private fun <T> chunkedRows(items: List<T>): List<List<T>> = when (items.size) {
    0 -> emptyList()
    1, 2 -> listOf(items)
    3 -> listOf(items.take(2), listOf(items[2]))
    4 -> listOf(items.take(2), items.drop(2))
    else -> items.chunked(3)
}

@Composable
private fun ParticipantBox(
    trackRef: TrackReference,
    modifier: Modifier = Modifier
) {
    var isSpeaking by remember(trackRef.participant) {
        mutableStateOf(trackRef.participant.isSpeaking)
    }

    // Status mute mic peserta ini (true = mic dimatikan)
    var isMuted by remember(trackRef.participant) {
        mutableStateOf(trackRef.publication?.muted ?: true)
    }

    LaunchedEffect(trackRef.participant, trackRef.publication) {
        // Sinkronkan kondisi awal saat box pertama tampil
        isMuted = trackRef.publication?.muted ?: true

        trackRef.participant.events.collect { event ->
            when (event) {
                is ParticipantEvent.SpeakingChanged ->
                    isSpeaking = event.participant.isSpeaking

                is ParticipantEvent.TrackMuted ->
                    if (event.publication.kind == Track.Kind.AUDIO) isMuted = true

                is ParticipantEvent.TrackUnmuted ->
                    if (event.publication.kind == Track.Kind.AUDIO) isMuted = false

                else -> Unit
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "speaking_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val borderColor = if (isSpeaking) colorPrimary else Color.Transparent

    Box(
        modifier = modifier                                  // ✅ ikut parent (fillMaxSize)
            .border(
                width = if (isSpeaking) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(2.dp)
            )
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0x80041F44))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Avatar Inisial
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSpeaking) colorPrimary else backgroundColor)
                    .border(
                        width = 2.dp,
                        color = if (isSpeaking) Color.White.copy(alpha = pulseAlpha) else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val initial = trackRef.participant.name?.take(1)?.uppercase() ?: "?"
                Text(text = initial, color = Color.White, fontSize = 10.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = trackRef.participant.name ?: "Unknown",
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
                color = Color.White,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Image(
                modifier = Modifier.size(16.dp),
                painter = painterResource(
                    id = if (isMuted) R.drawable.outline_mic_off_24
                    else R.drawable.outline_mic_24
                ),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    when {
                        isMuted -> dangerColor
                        !isMuted -> successColor
                        isSpeaking -> colorPrimary
                        else -> Color.LightGray
                    }
                )
            )
        }
    }
}