package torrent

import asBitList
import bencode.entity.PeerInfo
import getSHA1ForText
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import readNBytes
import toIntFour
import torrent.ByteParser.Companion.parseHandshake
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates
//TODO: send requests for new blocks
//TODO: answer for block requests
//TODO: implement choking algo
//TODO: mutlifile handling


@OptIn(ExperimentalUnsignedTypes::class)
class PeerHandler(
    val torrentLoader: TorrentLoader,
    val peerInfo: PeerInfo,
    val context: CoroutineContext,
    val socket: Socket
) {
    val peerConnection = PeerConnection(peerInfo, torrentLoader.metainfo.torrentInfo.pieces.size)

    lateinit var sendChannel: ByteWriteChannel
    lateinit var readChannel: ByteReadChannel

    val currentPieceBytes = ByteArray(torrentLoader.metainfo.torrentInfo.pieceLength.toInt())
    var currentPieceIndex by Delegates.notNull<Int>()
    var currentPieceDownloadedBytes = 0
    val pieceLength = torrentLoader.metainfo.torrentInfo.pieceLength.toInt()


    fun start() {
        runBlocking(context) {
            val handshake = torrentLoader.messageCreator.getHandshakeMessage()
            readChannel = socket.openReadChannel()
            sendChannel = socket.openWriteChannel(autoFlush = true)
            sendChannel.writeAvailable(handshake)
            readChannel.awaitContent()

            val handshakePeer = ByteArray(TorrentLoader.HANDSHAKE_SIZE)
            readChannel.readAvailable(handshakePeer, 0, 68)

            if (!parseHandshake(handshakePeer, torrentLoader.metainfo)) cancel()

            handleMessages()
        }
    }

    private suspend fun handleMessages() {
        while (true) {
            val len = readChannel.readNBytes(4).toIntFour()
            check(len >= 0)
            println("received new message")
            println("message length: $len")
            if (len == 0) {
                println("keep alive received")
                continue
            }
            val messageId = readChannel.readNBytes(1).first().toInt()
            println("received code: $messageId")
            println("------")
            when (messageId) {
                0 -> peerConnection.peerChoking = true
                1 -> peerConnection.peerChoking = false
                2 -> peerConnection.peerInterested = true
                3 -> peerConnection.peerInterested = false
                4 -> handleHave()
                5 -> handleBitfield(len)
                6 -> handlePeerRequest()
                7 -> handleBlock(len)
            }
        }

    }

    private suspend fun handlePeerRequest() {
        val pieceIndex = readChannel.readNBytes(4).toIntFour()
        if(!torrentLoader.alreadyDownloaded[pieceIndex]) {
            println("peer requested piece $pieceIndex that is not downloaded yet")
            return
        }
        val blockOffset = readChannel.readNBytes(4).toIntFour()
        val blockLength = readChannel.readNBytes(4).toIntFour()
        val block = torrentLoader.readBytes(pieceIndex, blockOffset, blockLength)
        val pieceMessage = torrentLoader.messageCreator.getPieceMessage(pieceIndex, blockOffset, block)
        sendChannel.writeAvailable(pieceMessage)
    }

    private suspend fun handleBlock(len: Int) {
        val blockLength = len -9
        val pieceIndex = readChannel.readNBytes(4).toIntFour()
        check(pieceIndex == currentPieceIndex)
        val blockOffset = readChannel.readNBytes(4).toIntFour()
        val blockData = readChannel.readNBytes(blockLength)
        writeToPiece(blockOffset, blockData)
    }

    private fun writeToPiece(blockOffset: Int, blockData: ByteArray) {
        System.arraycopy(blockData,0, currentPieceBytes, blockOffset, blockData.size)
        currentPieceDownloadedBytes+=blockData.size
        checkWritePiece()
    }

    private fun checkWritePiece() {
        var lengthToMeet = pieceLength
        if(isLastPiece()){
            lengthToMeet = torrentLoader.metainfo.torrentInfo.length!!.toInt() % pieceLength
        }
        if(currentPieceDownloadedBytes != lengthToMeet) return
        if(!validatePiece()){
            torrentLoader.putPieceBack(currentPieceIndex)
            getNewPiece()
            return
        }
        writePiece()
        getNewPiece()
    }

    private fun writePiece() {
        torrentLoader.writePieceToFile(currentPieceIndex, currentPieceBytes)

    }

    private fun getNewPiece() {
        val idx = torrentLoader.getNewPiece(peerConnection)
        if(idx == null) {
            context.cancel()
            return
        }
        currentPieceIndex = idx
        currentPieceDownloadedBytes = 0
    }

    private fun validatePiece(): Boolean {
        return getSHA1ForText(sha1(currentPieceBytes)) == torrentLoader.metainfo.torrentInfo.pieces[currentPieceIndex]
    }

    private fun isLastPiece(): Boolean {
        return torrentLoader.metainfo.torrentInfo.pieces.lastIndex == currentPieceIndex
    }

    private suspend fun handleHave() {
        val pieceIndex = readChannel.readNBytes(4).toIntFour()
        peerConnection.availablePieces[pieceIndex] = true
    }

    private suspend fun handleBitfield(len: Int) {
        val bitfieldLength = len - 1
        val bitfieldBytes = readChannel.readNBytes(bitfieldLength)
        val bitfieldUBytes = bitfieldBytes.toUByteArray()
        val bitfield = bitfieldUBytes.asBitList()
        check(bitfieldLength >= peerConnection.availablePieces.size)
        peerConnection.availablePieces.indices.forEach {
            peerConnection.availablePieces[it] = bitfield[it]
        }
        if (torrentLoader.somethingDownloaded()) {
            val ownBitfield = torrentLoader.getBitfieldMessage()
            sendChannel.writeAvailable(ownBitfield)
        }
    }


}
