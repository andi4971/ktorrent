package bencode

class Metainfo(
    val announceURL: String,
    val announceList: List<String>,
    val comment: String?,
    val createdBy: String?,
    val encoding: String?,
    val torrentInfo: TorrentInfo
) {
}

data class TorrentInfo(
    val name: String,
    val pieceLength: Long,
    val pieces: StringParsedData,
    val length: Long?,
    val files: DictionaryParsedData?
)
