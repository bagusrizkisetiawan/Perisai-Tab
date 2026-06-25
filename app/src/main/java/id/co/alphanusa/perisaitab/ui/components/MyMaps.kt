package id.co.alphanusa.perisaitab.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import id.co.alphanusa.perisaitab.data.local.AppSettingsManager
import id.co.alphanusa.perisaitab.data.remote.api.ApiConfig
import id.co.alphanusa.perisaitab.data.remote.response.DrawMapItem
import id.co.alphanusa.perisaitab.ui.viewmodel.DrawViewModel
import id.co.alphanusa.perisaitab.ui.viewmodel.DrawViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import android.graphics.DashPathEffect
import androidx.core.graphics.drawable.DrawableCompat
import id.co.alphanusa.perisaitab.R
import org.osmdroid.views.overlay.Overlay
import android.graphics.Paint
import id.co.alphanusa.perisaitab.utils.GoogleSatelliteTile

@Composable
fun OsmdroidMapView(
    modifier: Modifier = Modifier,
    deviceLocation: GeoPoint? = null,
    deviceMarkerTitle: String = "Lokasi Saya",
    deviceMarkerIcon: Int? = null,
    initialZoom: Double = 19.0,
    pocYaw: Float? = null,
    followDevice: Boolean = true
) {
    val context = LocalContext.current

    // ── Setup auth & API ───────────────────────────────────────────────────
    val authManager = remember { ApiConfig.getInstance(context) }
    val httpClient = remember { authManager.getHttpClient() }
    val drawApi = remember { authManager.apiService }
    val drawVm: DrawViewModel = viewModel(factory = DrawViewModelFactory(drawApi))

    val settingsManager = remember { AppSettingsManager.getInstance(context) }
    val baseUrl = remember {
        settingsManager.getBaseUrl().let { if (!it.endsWith("/")) "$it/" else it }
    }

    val drawItems by drawVm.drawItems.collectAsState()
    var bounds by remember { mutableStateOf<BoundingBox?>(null) }

    // ── Cache icon per iconId (Map<id, Drawable>) ──────────────────────────
    var iconCache by remember { mutableStateOf<Map<String, Drawable>>(emptyMap()) }

    // Setiap kali drawItems berubah, download iconId yang belum ada di cache
    LaunchedEffect(drawItems) {
        val neededIds = drawItems
            .filter { it.type.equals("pin", ignoreCase = true) }
            .mapNotNull { it.icon?.takeIf { id -> id.isNotBlank() } }
            .toSet()

        val missing = neededIds - iconCache.keys
        if (missing.isEmpty()) return@LaunchedEffect

        val fetched = mutableMapOf<String, Drawable>()
        missing.forEach { iconId ->
            val url = "${baseUrl}v1/sticker/$iconId/media"
            loadDrawableWithAuth(context, url, httpClient)?.let { drawable ->
                fetched[iconId] = drawable
            }
        }
        if (fetched.isNotEmpty()) {
            iconCache = iconCache + fetched
        }
    }

    LaunchedEffect(bounds) {
        bounds?.let { b ->
            drawVm.fetchDraw(
                long1 = b.lonWest,
                lat1  = b.latNorth,
                long2 = b.lonEast,
                lat2  = b.latSouth
            )
        }
    }

    val arrowDrawable = remember {
        ContextCompat.getDrawable(context, R.drawable.arrow)
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    val initialCenter = remember { GeoPoint(-6.9828, 110.4091) }

    LaunchedEffect(deviceLocation, followDevice, mapViewRef) {
        if (!followDevice) return@LaunchedEffect
        val mv = mapViewRef ?: return@LaunchedEffect
        val loc = deviceLocation ?: return@LaunchedEffect

        mv.controller.animateTo(loc)
        // update bounds setelah animasi supaya data ke-fetch untuk area baru
        mv.postDelayed({ bounds = mv.boundingBox }, 600)
    }


    Card(modifier = modifier, shape = RoundedCornerShape(4.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        mapViewRef = this
                        setTileSource(GoogleSatelliteTile)
//                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        controller.setZoom(initialZoom)
                        controller.setCenter(deviceLocation ?: initialCenter)
                        addMapListener(
                            DelayedMapListener(
                                object : MapListener {
                                    override fun onScroll(e: ScrollEvent?): Boolean {
                                        bounds = boundingBox
                                        return false
                                    }
                                    override fun onZoom(e: ZoomEvent?): Boolean {
                                        bounds = boundingBox
                                        return false
                                    }
                                },
                                500
                            )
                        )
                        post { bounds = boundingBox }
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    drawItems.forEach { item ->
                        when (item.type.lowercase()) {
                            "pin"    -> item.toMarker(mapView, iconCache)?.let(mapView.overlays::add)
                            "line"   -> item.toPolyline(mapView, arrowDrawable)?.let {
                                mapView.overlays.addAll(it)            // ← addAll, bukan add
                            }
                            "area"   -> item.toPolygon()?.let(mapView.overlays::add)
                            "circle" -> item.toCircle()?.let(mapView.overlays::add)
                        }
                    }

                    val deviceMarker = Marker(mapView).apply {
                        position = deviceLocation
                        title = deviceMarkerTitle
                        setAnchor(Marker.ANCHOR_CENTER, 5f / 8f)
                        deviceMarkerIcon?.let { iconRes ->
                            ContextCompat.getDrawable(mapView.context, iconRes)?.let {
                                icon = resizeDrawableByWidth(mapView.context, it, 48)
                            }
                        }
                        pocYaw?.let { yaw ->
                            rotation = (360 - yaw) % 360
                            isFlat = true
                        }
                    }
                    mapView.overlays.add(deviceMarker)

                    mapView.invalidate()
                }
            )
        }
    }
}

// ── Extensions ─────────────────────────────────────────────────────────────

private fun DrawMapItem.parseColor(default: Int = android.graphics.Color.BLUE): Int =
    runCatching { android.graphics.Color.parseColor(color) }.getOrDefault(default)

/**
 * Pin marker. Icon di-resolve dari [iconCache] berdasarkan field [DrawMapItem.icon] (UUID).
 * Ukuran pakai field [DrawMapItem.size] (default 48 px kalau 0).
 */
private fun DrawMapItem.toMarker(
    mapView: MapView,
    iconCache: Map<String, Drawable>
): Marker? {
    val p = point ?: return null
    val iconId = this.icon?.takeIf { it.isNotBlank() } ?: return null
    val resolvedIcon = iconCache[iconId] ?: return null

    return Marker(mapView).apply {
        position = GeoPoint(p.lat, p.long)
        title = this@toMarker.name ?: ""
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

        if (resolvedIcon != null) {
            val baseSize = size.toInt().takeIf { it > 0 } ?: 64
            val targetWidth = baseSize * 1.5
            icon = resizeDrawableByWidth(mapView.context, resolvedIcon, targetWidth.toInt())
        }
    }
}

private fun DrawMapItem.toPolyline(
    mapView: MapView,
    arrowDrawable: Drawable?
): List<Overlay>? {
    val pts = points?.takeIf { it.size >= 2 } ?: return null
    val geo = pts.map { GeoPoint(it.lat, it.long) }
    val color = parseColor()
    val stroke = (size.takeIf { it > 0 } ?: 6.0).toFloat()

    val overlays = mutableListOf<Overlay>()

    // 🔹 1. Garis putus-putus
    val polyline = Polyline().apply {
        setPoints(geo)
        outlinePaint.color = color
        outlinePaint.strokeWidth = stroke
        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(24f, 12f), 0f)
        title = this@toPolyline.name ?: ""
    }
    overlays.add(polyline)

    // 🔹 2. Dot di titik AWAL
    val dotSize = (stroke * 4).toInt().coerceAtLeast(20)
    overlays.add(makeDotMarker(mapView, geo.first(), color, dotSize))

    // 🔹 3. Arrow di titik AKHIR
    if (arrowDrawable != null) {
        val tinted = arrowDrawable.constantState?.newDrawable()?.mutate()
            ?: arrowDrawable.mutate()
        DrawableCompat.setTint(tinted, color)

        val arrowSize = (stroke * 6).toInt().coerceAtLeast(32)
        val endBearing = geo[geo.size - 2].bearingTo(geo.last()).toFloat()
        overlays.add(makeArrowMarker(mapView, geo.last(), tinted, endBearing, arrowSize))
    }

    return overlays
}

/** Bikin marker dot (lingkaran solid) di posisi tertentu. */
private fun makeDotMarker(
    mapView: MapView,
    position: GeoPoint,
    color: Int,
    sizePx: Int
): Marker = Marker(mapView).apply {
    this.position = position
    icon = createDotDrawable(mapView.context, color, sizePx)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    setInfoWindow(null)
}

/** Generate bitmap berbentuk lingkaran solid. */
private fun createDotDrawable(
    context: Context,
    color: Int,
    sizePx: Int,
    withWhiteBorder: Boolean = true
): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    // Border putih (opsional, bikin dot lebih kelihatan di atas line)
    if (withWhiteBorder) {
        val borderPaint = Paint().apply {
            this.color = android.graphics.Color.WHITE
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, sizePx / 2f, borderPaint)
    }

    // Lingkaran utama
    val fillPaint = Paint().apply {
        this.color = color
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    val innerRadius = if (withWhiteBorder) sizePx / 2f - sizePx * 0.15f else sizePx / 2f
    canvas.drawCircle(cx, cy, innerRadius, fillPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun makeArrowMarker(
    mapView: MapView,
    position: GeoPoint,
    drawable: Drawable,
    bearing: Float,
    size: Int
): Marker = Marker(mapView).apply {
    this.position = position
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    icon = resizeDrawableByWidth(mapView.context, drawable, size)
    rotation = -bearing       // osmdroid rotation: counter-clockwise; bearing: clockwise from north
    isFlat = true             // ikut rotasi peta
    setInfoWindow(null)       // disable popup saat di-tap
}

private fun DrawMapItem.toPolygon(): Polygon? {
    val pts = points?.takeIf { it.size >= 3 } ?: return null
    return Polygon().apply {
        points = pts.map { GeoPoint(it.lat, it.long) }
        val c = parseColor()
        fillPaint.color = (c and 0x00FFFFFF) or 0x40000000
        outlinePaint.color = c
        outlinePaint.strokeWidth = (size.takeIf { it > 0 } ?: 3.0).toFloat()
        title = this@toPolygon.name ?: ""
    }
}

private fun DrawMapItem.toCircle(): Polygon? {
    val p = point ?: return null
    return Polygon().apply {
        points = Polygon.pointsAsCircle(GeoPoint(p.lat, p.long), radius)
        val c = parseColor()
        fillPaint.color = (c and 0x00FFFFFF) or 0x40000000
        outlinePaint.color = c
        outlinePaint.strokeWidth = (size.takeIf { it > 0 } ?: 3.0).toFloat()
        title = this@toCircle.name ?: ""
    }
}

// ── Loader: download drawable lewat OkHttp (sudah ada auth header) ─────────
suspend fun loadDrawableWithAuth(
    context: Context,
    url: String,
    client: OkHttpClient
): Drawable? = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                android.util.Log.w("OsmIcon", "Gagal load $url → HTTP ${response.code}")
                return@use null
            }
            val body = response.body ?: return@use null
            val bmp = BitmapFactory.decodeStream(body.byteStream())
            bmp?.let { BitmapDrawable(context.resources, it) }
        }
    }.onFailure {
        android.util.Log.e("OsmIcon", "Error load $url", it)
    }.getOrNull()
}

// ── Helper resize (tetap) ──────────────────────────────────────────────────
fun resizeDrawableByWidth(context: Context, drawable: Drawable, targetWidth: Int): BitmapDrawable {
    val bitmap = if (drawable is BitmapDrawable) drawable.bitmap else {
        val bmp = Bitmap.createBitmap(
            drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
            Bitmap.Config.ARGB_8888
        )
        Canvas(bmp).also {
            drawable.setBounds(0, 0, it.width, it.height); drawable.draw(it)
        }
        bmp
    }
    val ratio = bitmap.height.toFloat() / bitmap.width.toFloat()
    val targetHeight = (targetWidth * ratio).toInt()
    val resized = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    return BitmapDrawable(context.resources, resized)
}