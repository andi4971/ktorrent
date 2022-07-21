package torrent

import bittorrentProtocol
import toBitString
import torrent.TorrentLoader.Companion.HANDSHAKE_SIZE
import java.util.HexFormat

class ByteParser {

    companion object {
        private val hex = HexFormat.of()

        fun parseHandshake(handshake: ByteArray, infoHash: String): Handshake {
            check(handshake.size == HANDSHAKE_SIZE) {"handshake not 68 bytes ${handshake.size}"}
            val pstrLen = handshake.first().toInt()
            val pstr = handshake.sliceArray(1..19).toString(Charsets.UTF_8)
            val reservedBytes =  handshake.sliceArray(20..27)
            val handshakeInfoHash = hex.formatHex(handshake.sliceArray(28.. 47))
            val peerId = handshake.sliceArray(48 until HANDSHAKE_SIZE).toString(Charsets.UTF_8)
            check(handshakeInfoHash == infoHash) {"different info hash: expected $infoHash got $handshakeInfoHash"}
            check(pstrLen== 19) {"prstlen not 19 byte was: $pstrLen"}
            check(pstr  == bittorrentProtocol) {"protocol different was $pstr"}
            val reservedBits = reservedBytes.joinToString("|"){it.toBitString()}
            return Handshake(pstr, infoHash,reservedBits, peerId)
        }
    }

}

data class Handshake(val pstr: String, val infoHash: String,val reservedBits: String, val peerId: String)
