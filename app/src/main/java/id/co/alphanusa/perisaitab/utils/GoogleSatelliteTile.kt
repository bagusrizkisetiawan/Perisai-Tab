package id.co.alphanusa.perisaitab.utils

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

object GoogleSatelliteTile :
    OnlineTileSourceBase(
        "Google-Satellite",
        0, 20, 256, ".png",
        arrayOf("https://mt0.google.com/vt/lyrs=s&x={x}&y={y}&z={z}")
    ) {

    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl
            .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
            .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
            .replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
    }
}
