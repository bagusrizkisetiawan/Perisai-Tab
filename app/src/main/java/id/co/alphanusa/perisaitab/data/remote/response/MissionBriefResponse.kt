package id.co.alphanusa.perisaitab.data.remote.response

data class MissionBriefLocation(
    val long: Double? = null,
    val lat: Double? = null,
)

data class MissionBriefItem(
    val CreatedAt: String? = null,
    val UpdatedAt: String? = null,
    val DeletedAt: String? = null,
    val ID: String? = null,
    val Unit: String? = null,
    val Time: String? = null,
    val Activities: String? = null,
    val PersonnelsEquipments: String? = null,
    val Location: MissionBriefLocation? = null,
)

data class MissionBriefMeta(
    val total_count: Int? = null,
    val total_page: Int? = null,
    val page: Int? = null,
    val count: Int? = null,
)

data class MissionBriefResponse(
    val message: String? = null,
    val data: List<MissionBriefItem> = emptyList(),
    val meta: MissionBriefMeta? = null,
)
