package bencode.entity

import java.nio.file.Path

class Metainfo(
    val announceURL: String,
    val announceList: List<String>,
    val comment: String?,
    val createdBy: String?,
    val encoding: String?,
    val torrentInfo: TorrentInfo,
    val infoHash: ByteArray,
    val infoHashForUrl: String,
    val infoHashForText: String
) {
}

data class TorrentInfo(
    val name: String,
    val pieceLength: Long,
    val pieces: List<String>,
    val length: Long?,
    val files: List<TorrentFile>?
)

data class TorrentFile(
    val path: Path,
    val length: Long
)

