package id.co.alphanusa.perisaitab.data.remote.response

data class MissionNoteImage(
    val ID: String? = null,
    val FileName: String? = null,
    val url: String? = null,
)

data class MissionNoteItem(
    val ID: String? = null,
    val CreatedAt: String? = null,
    val UpdatedAt: String? = null,
    val DeletedAt: String? = null,
    val Title: String? = null,
    val Note: String? = null,
    val Images: List<MissionNoteImage> = emptyList(),
)

data class MissionNoteMeta(
    val page: Int? = null,
    val size: Int? = null,
    val total: Int? = null,
    val totalPages: Int? = null,
)

// Catatan: server mengembalikan `data` LANGSUNG sebagai array note (bukan objek
// { items, meta } seperti sebagian spec). Jadi `data` dimodelkan sebagai List.
data class MissionNoteResponse(
    val message: String? = null,
    val data: List<MissionNoteItem> = emptyList(),
    val meta: MissionNoteMeta? = null,
)
