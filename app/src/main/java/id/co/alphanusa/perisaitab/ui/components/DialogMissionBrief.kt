package id.co.alphanusa.perisaitab.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import id.co.alphanusa.perisaitab.R
import id.co.alphanusa.perisaitab.data.local.AppSettingsManager
import id.co.alphanusa.perisaitab.data.remote.api.ApiConfig
import id.co.alphanusa.perisaitab.data.remote.api.ApiService
import id.co.alphanusa.perisaitab.data.remote.response.MissionBriefItem
import id.co.alphanusa.perisaitab.data.remote.response.MissionNoteItem
import id.co.alphanusa.perisaitab.ui.viewmodel.MissionBriefViewModel
import id.co.alphanusa.perisaitab.ui.viewmodel.MissionBriefViewModelFactory
import okhttp3.OkHttpClient

@Composable
fun DialogMissionBrief(
    onDismiss: () -> Unit,
    apiService: ApiService,
) {
    val context = LocalContext.current
    val httpClient = remember { ApiConfig.getInstance(context).getHttpClient() }
    val baseUrl = remember { AppSettingsManager.getInstance(context).getBaseUrl() }

    val vm: MissionBriefViewModel = viewModel(
        factory = MissionBriefViewModelFactory(apiService),
        key = "MissionBriefViewModel",
    )
    val items by vm.items.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val notes by vm.notes.collectAsState()
    val notesLoading by vm.notesLoading.collectAsState()
    val notesError by vm.notesError.collectAsState()

    // Fetch ulang tiap kali dialog dibuka (komposisi masuk).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.refresh()
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
                        text = "Mission Brief",
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

            // Konten scrollable: Rundown + Notes jadi satu gulungan.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .padding(top = 42.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Mission Rundown",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                MissionRundownTable(
                    items = items,
                    isLoading = isLoading,
                    error = error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "Mission Notes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                MissionNotesSection(
                    notes = notes,
                    isLoading = notesLoading,
                    error = notesError,
                    httpClient = httpClient,
                    baseUrl = baseUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── Mission Rundown (tabel) ─────────────────────────────────────────────────

// Bobot lebar kolom (mengikuti proporsi desain rundown).
private const val W_NO = 0.5f
private const val W_UNIT = 1.4f
private const val W_TIME = 1.4f
private const val W_ACT = 3.4f
private const val W_PERS = 1.6f

@Composable
private fun MissionRundownTable(
    items: List<MissionBriefItem>,
    isLoading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = 10.dp)
        ) {
            HeaderCell("#", W_NO)
            VDivider()
            HeaderCell("Unit", W_UNIT)
            VDivider()
            HeaderCell("Time", W_TIME)
            VDivider()
            HeaderCell("Activities and Materials", W_ACT)
            VDivider()
            HeaderCell("Pers and Equip", W_PERS)
        }
        HDivider(alpha = 0.9f)

        when {
            isLoading -> LoadingRow()
            error != null -> InfoText(error, dangerColor)
            items.isEmpty() -> InfoText("Belum ada data mission rundown.", Color.Gray)
            else -> items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 12.dp)
                ) {
                    DataCell("${index + 1}", W_NO)
                    VDivider()
                    DataCell(item.Unit ?: "-", W_UNIT)
                    VDivider()
                    DataCell(item.Time ?: "-", W_TIME)
                    VDivider()
                    DataCell(item.Activities ?: "-", W_ACT)
                    VDivider()
                    DataCell(item.PersonnelsEquipments ?: "-", W_PERS)
                }
                HDivider(alpha = 0.35f)
            }
        }
    }
}

// ── Mission Notes ───────────────────────────────────────────────────────────

@Composable
private fun MissionNotesSection(
    notes: List<MissionNoteItem>,
    isLoading: Boolean,
    error: String?,
    httpClient: OkHttpClient,
    baseUrl: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HDivider(alpha = 0.9f)
        when {
            isLoading -> LoadingRow()
            error != null -> InfoText(error, dangerColor)
            notes.isEmpty() -> InfoText("Belum ada mission notes.", Color.Gray)
            else -> notes.forEach { note ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                ) {
                    Text(
                        text = note.Title ?: "-",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = note.Note ?: "",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color.White,
                    )
                    if (note.Images.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            note.Images.forEach { img ->
                                NoteImage(
                                    url = buildImageUrl(baseUrl, img.url),
                                    httpClient = httpClient,
                                    modifier = Modifier
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                )
                            }
                        }
                    }
                }
                HDivider(alpha = 0.35f)
            }
        }
    }
}

@Composable
private fun NoteImage(
    url: String,
    httpClient: OkHttpClient,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(url) {
        if (url.isBlank()) {
            failed = true
            return@LaunchedEffect
        }
        val drawable = loadDrawableWithAuth(context, url, httpClient)
        val bmp = (drawable as? BitmapDrawable)?.bitmap
        if (bmp != null) bitmap = bmp.asImageBitmap() else failed = true
    }

    val bmp = bitmap
    when {
        // Tinggi tetap (dari modifier), lebar menyesuaikan rasio → tidak terpotong.
        bmp != null -> Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
        // Placeholder loading/gagal: kotak persegi mengikuti tinggi yang sama.
        else -> Box(
            modifier = modifier
                .width(200.dp)
                .background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            if (failed) {
                Image(
                    painter = painterResource(id = R.drawable.outline_close_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.Gray),
                    modifier = Modifier.size(24.dp),
                )
            } else {
                CircularProgressIndicator(
                    color = colorPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Bangun URL media dari field `url` response (sudah berisi token media_access
 * yang benar). Cukup prefix base URL bila path-nya relatif.
 */
private fun buildImageUrl(base: String, path: String?): String {
    if (path.isNullOrBlank()) return ""
    if (path.startsWith("http")) return path
    return base.trimEnd('/') + "/" + path.trimStart('/')
}

// ── Helper sel/divider ──────────────────────────────────────────────────────

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        color = colorPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 10.dp),
    )
}

@Composable
private fun RowScope.DataCell(text: String, weight: Float) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 10.dp),
    )
}

@Composable
private fun RowScope.VDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(colorPrimary.copy(alpha = 0.5f))
    )
}

@Composable
private fun HDivider(alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colorPrimary.copy(alpha = alpha))
    )
}

@Composable
private fun LoadingRow() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = colorPrimary)
    }
}

@Composable
private fun InfoText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        modifier = Modifier.padding(24.dp),
    )
}
