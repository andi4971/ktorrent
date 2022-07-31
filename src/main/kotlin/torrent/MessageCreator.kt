package torrent

import bencode.entity.Metainfo
import bittorrentProtocol
import copyIntoSelf
import toByteArray
import kotlin.math.ceil
import kotlin.math.floor

typealias Block = Pair<Int, ByteArray>

class MessageCreator(private val metainfo: Metainfo) {

    companion object{
        const val BLOCK_SIZE = 2048
        //const val BLOCK_SIZE = 4096
        val BLOCK_SIZE_BYTES = BLOCK_SIZE.toByteArray()
        const val ID_5 = (5).toByte()
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
            .map { it.joinToString("").padEnd(8,'0').toUByte(2).toByte() }
        return (bitfieldLength.toList() + listOf(ID_5) +bitfieldBytes).toByteArray()
    }

    fun getBlockRequestsForPiece(pieceIndex: Int): MutableList<Pair<Int, ByteArray>> {
        val pieceLength = metainfo.torrentInfo.pieceLength.toInt()
        val messages = mutableListOf<Pair<Int, ByteArray>>()
        val pieceIndexBytes = pieceIndex.toByteArray()

        val isLastPiece = metainfo.torrentInfo.pieces.lastIndex == pieceIndex

        var overshootBytes = 0
        val until = if(metainfo.torrentInfo.pieces.lastIndex == pieceIndex) {
            val remainingBytes = metainfo.torrentInfo.getLength().mod(pieceLength).toDouble()
            val untilFloor = floor(remainingBytes/BLOCK_SIZE).toInt() * BLOCK_SIZE
            overshootBytes = remainingBytes.toInt() - untilFloor
            untilFloor
        }else{
            pieceLength
        }

        for(start in 0 ..  until- BLOCK_SIZE step BLOCK_SIZE) {
            val message = ByteArray(ID_6_MESSAGE_LENGTH)
            message.copyIntoSelf(ID_6_LENGTH_BYTES,0)
            message.copyIntoSelf(ID_6_ARR,4)
            message.copyIntoSelf(pieceIndexBytes, 5)
            message.copyIntoSelf(start.toByteArray(), 9)
            if(start  >= until- BLOCK_SIZE){
                val blockSizeBytes = (BLOCK_SIZE+overshootBytes).toByteArray()
                message.copyIntoSelf(blockSizeBytes, 13)
            }else{
                message.copyIntoSelf(BLOCK_SIZE_BYTES, 13)
            }

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
