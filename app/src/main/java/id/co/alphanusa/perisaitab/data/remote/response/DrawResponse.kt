package id.co.alphanusa.perisaitab.data.remote.response

import com.google.gson.annotations.SerializedName

data class LatLong(
    @SerializedName(
        value = "Lat",
        alternate = ["lat", "Latitude", "latitude"]
    )
    val lat: Double,

    @SerializedName(
        value = "Long",
        alternate = ["long", "lng", "Lng", "Longitude", "longitude", "Lon", "lon"]
    )
    val long: Double
)

data class DrawMapAttachment(
    @SerializedName("Url")  val url: String? = null,
    @SerializedName("Name") val name: String? = null
)

data class DrawMapItem(
    @SerializedName("ID")         val id: String,
    @SerializedName("CreatedAt")  val createdAt: String,
    @SerializedName("UpdatedAt")  val updatedAt: String,
    @SerializedName("DeletedAt")  val deletedAt: String?,
    @SerializedName("Type")       val type: String,
    @SerializedName("Name")       val name: String? = null,
    @SerializedName("Color")      val color: String? = null,
    @SerializedName("Point")      val point: LatLong? = null,        // ✅ nullable
    @SerializedName("Points")     val points: List<LatLong>? = null, // ✅ nullable
    @SerializedName("Radius")     val radius: Double = 0.0,
    @SerializedName("Size")       val size: Double = 0.0,
    @SerializedName("Icon")       val icon: String? = null,
    @SerializedName("Notes")      val notes: List<String>? = null,
    @SerializedName("Attachment") val attachment: DrawMapAttachment? = null
)

data class DrawResponse(
    @SerializedName("data")    val data: List<DrawMapItem>? = null,
    @SerializedName("message") val message: String? = null
)
