package id.co.alphanusa.perisaitab.data.remote.response

data class ParticipantsResponse(
    val status: String,
    val message: String,
    val data: List<Participant>
)

data class Participant(
    val identity: String,
    val joined_at: Long,
    val name: String,
    val sid: String,
    val state: String
)
