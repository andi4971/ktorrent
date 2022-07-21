package bencode.entity

data class TrackerResponse(
    val failureReason: String?,
    val warningMessage: String?,
    val interval: Long,
    val minInterval: Long?,
    val trackerId: String?,
    val complete: Long,
    val incomplete: Long,
    val peers: List<PeerInfo>
)

data class PeerInfo(
    val peerId: String,
    val ip: String,
    val port: Int
)
