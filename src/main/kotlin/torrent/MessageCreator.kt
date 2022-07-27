package torrent

import bencode.entity.Metainfo
import bittorrentProtocol
import copyInto
import toByteArray
import kotlin.math.ceil

class MessageCreator(private val metainfo: Metainfo) {

    companion object{
        const val BLOCK_SIZE = 2048
        const val ID_5 = (5).toByte()
        const val ID_6 = (6).toByte()
        val ID_6_ARR = ByteArray(1){6}
    }

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

    fun getBitfieldMessage(havePieces: Array<Boolean>): ByteArray {
        val bitfieldLength = (ceil(havePieces.size/8.0)+1).toInt().toByteArray()
        val bitfieldBytes = havePieces
            .map { if(it) '1' else '0' }
            .chunked(8)
            .map { it.joinToString("").padEnd(8,'0').toByte() }
        return (bitfieldLength.toList() + listOf(ID_5) +bitfieldBytes).toByteArray()
    }

    fun getBlockRequestsForPiece(pieceIndex: Long): MutableList<ByteArray> {

    }

    fun getPieceMessage(pieceIndex: Int, blockOffset: Int, block: ByteArray): ByteArray {
        val length = 9+block.size
        val pieceMessage = ByteArray(length)
        val lengthBytes = length.toByteArray()
        val indexBytes = pieceIndex.toByteArray()
        val offsetBytes = blockOffset.toByteArray()
        pieceMessage.copyInto(lengthBytes, 0)
        pieceMessage.copyInto(ID_6_ARR, 4)
        pieceMessage.copyInto(indexBytes, 5)
        pieceMessage.copyInto(offsetBytes, 9)
        pieceMessage.copyInto(block, 13)
        return pieceMessage
    }
}
