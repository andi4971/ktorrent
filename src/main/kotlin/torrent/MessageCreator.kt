package torrent

import bencode.entity.Metainfo
import bittorrentProtocol

class MessageCreator(private val metainfo: Metainfo) {

    fun getHandshakeMessage(): ByteArray {
        val bytes = mutableListOf<Byte>()
        val pstrlen = (19).toByte()
        bytes.add(pstrlen)
        val pstr = bittorrentProtocol.encodeToByteArray()
        bytes += pstr.toList()
        bytes += (0 until 8).map { (0).toByte() }.toList()
        bytes += metainfo.infoHash.toList()
        bytes += TorrentLoader.peerId.encodeToByteArray().toList()
        return bytes.toByteArray()
    }
}
