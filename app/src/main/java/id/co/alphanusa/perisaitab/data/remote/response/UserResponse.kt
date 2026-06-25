package id.co.alphanusa.perisaitab.data.remote.response

data class UserData(
    val CreatedAt: String? = null,
    val UpdatedAt: String? = null,
    val DeletedAt: String? = null,
    val ID: String? = null,
    val Name: String? = null,
    val Type: String? = null,
    val SerialNumber: String? = null,
    val LocationName: String? = null,
    val Latitude: Double? = null,
    val Longitude: Double? = null
)

data class UserResponse(
    val message: String,
    val data: UserData
)
