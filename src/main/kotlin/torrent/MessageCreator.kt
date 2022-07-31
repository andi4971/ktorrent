package torrent

import bencode.entity.Metainfo
import bittorrentProtocol
import copyIntoSelf
import toByteArray
import java.lang.Math.pow
import kotlin.math.ceil
import kotlin.math.pow

typealias Block = Pair<Int, ByteArray>

class MessageCreator(private val metainfo: Metainfo) {

    companion object{
        //val BLOCK_SIZE = 2.0.pow(14.0).toInt()
        val BLOCK_SIZE = 2048
        //val BLOCK_SIZE = 4096
        val ONE_BYTES = (1).toByteArray()
        val BLOCK_SIZE_BYTES = BLOCK_SIZE.toByteArray()
        const val ID_5 = (5).toByte()
        const val ID_6 = (6).toByte()
        val ID_6_LENGTH_BYTES = (13).toByteArray()
        val ID_6_ARR = ByteArray(1){6}
        val ID_7_ARR = ByteArray(1){7}
        const val ID_6_MESSAGE_LENGTH = 17
        val CHOKE_MESSAGE = byteArrayOf(0,0,0,1,0)
        val UNCHOKE_MESSAGE = byteArrayOf(0,0,0,1,1)
        val INTERESTED_MESSAGE = byteArrayOf(0,0,0,1,2)
        val UNINTERESTED_MESSAGE = byteArrayOf(0,0,0,1,3)


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

    fun getBlockRequestsForPiece(pieceIndex: Int): MutableList<Pair<Int, ByteArray>> {
        val pieceLength = metainfo.torrentInfo.pieceLength.toInt()
        val messages = mutableListOf<Pair<Int, ByteArray>>()
        val pieceIndexBytes = pieceIndex.toByteArray()
        for(start in 0 until pieceLength- BLOCK_SIZE step BLOCK_SIZE) {

            val message = ByteArray(ID_6_MESSAGE_LENGTH)
            message.copyIntoSelf(ID_6_LENGTH_BYTES,0)
            message.copyIntoSelf(ID_6_ARR,4)
            message.copyIntoSelf(pieceIndexBytes, 5)
            message.copyIntoSelf(start.toByteArray(), 9)
            message.copyIntoSelf(BLOCK_SIZE_BYTES, 13)

            messages+=Pair(start, message)
        }
        return messages
    }

    fun getPieceMessage(pieceIndex: Int, blockOffset: Int, block: ByteArray): ByteArray {
        val length = 9+block.size
        val pieceMessage = ByteArray(length)
        val lengthBytes = length.toByteArray()
        val indexBytes = pieceIndex.toByteArray()
        val offsetBytes = blockOffset.toByteArray()
        pieceMessage.copyIntoSelf(lengthBytes, 0)
        pieceMessage.copyIntoSelf(ID_7_ARR, 4)
        pieceMessage.copyIntoSelf(indexBytes, 5)
        pieceMessage.copyIntoSelf(offsetBytes, 9)
        pieceMessage.copyIntoSelf(block, 13)
        return pieceMessage
    }

}
