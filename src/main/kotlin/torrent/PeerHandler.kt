package torrent

import asBitList
import bencode.entity.PeerInfo
import connectToPeer
import copyIntoSelf
import getSHA1ForText
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.slf4j.LoggerFactory
import toIntFour
import torrent.HandshakeParser.Companion.parseHandshake
import torrent.MessageCreator.Companion.BLOCK_SIZE
import torrent.MessageCreator.Companion.CHOKE_MESSAGE
import torrent.MessageCreator.Companion.INTERESTED_MESSAGE
import torrent.MessageCreator.Companion.UNCHOKE_MESSAGE
import torrent.MessageCreator.Companion.UNINTERESTED_MESSAGE
import kotlin.properties.Delegates


@OptIn(ExperimentalUnsignedTypes::class)
class PeerHandler(
    private val torrentLoader: TorrentLoader,
    val scope: CoroutineScope

) {
    companion object {
        const val SIMULTANEOUS_REQUESTS = 10
    }
    private val LOG = LoggerFactory.getLogger(this::class.java)

    lateinit var peerConnection: PeerConnection
    private lateinit var socket: Socket
    lateinit var sendChannel: ByteWriteChannel
    lateinit var readChannel: ByteReadChannel

    private val currentPieceBytes = ByteArray(torrentLoader.metainfo.torrentInfo.pieceLength.toInt())
    private var currentPieceIndex by Delegates.notNull<Int>()
    private var gotPiece = false
    var currentPieceDownloadedBytes = 0
    private lateinit var blockRequestsForCurrentPiece: MutableList<Block>
    private val currentlyWaitingBlockRequests = mutableListOf<Block>()
    private var keepAliveCount = 0
    private lateinit var selectorManager: SelectorManager

    val bytesToSend = Channel<ByteArray>()

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    suspend fun start() {
        scope.launch(scope.coroutineContext) {
            selectorManager = SelectorManager(scope.coroutineContext)
            var tempSocket: Socket? = null
            var peerInfo: PeerInfo = torrentLoader.availablePeers.receive()
            while (tempSocket == null) {
                tempSocket = connectToPeer(peerInfo, LOG, selectorManager)
                if (tempSocket == null) {
                    if(torrentLoader.availablePeers.isEmpty){
                        LOG.warn("No more available peers")
                    }
                    peerInfo = torrentLoader.availablePeers.receive()
                }
            }
            socket = tempSocket
            readChannel = socket.openReadChannel()
            sendChannel = socket.openWriteChannel(autoFlush = true)
            peerConnection = PeerConnection(peerInfo, torrentLoader.metainfo.torrentInfo.pieces.size)
            async(scope.coroutineContext) {
                startConnection(scope)
            }
            async (scope.coroutineContext) {
                receiveBytesToSend(scope)
            }
        }
    }

    private suspend fun receiveBytesToSend(scope: CoroutineScope) {
        while (scope.isActive) {
            val bytes = bytesToSend.receive()
            sendChannel.writeAvailable(bytes)
        }
    }

    private suspend fun startConnection(scope: CoroutineScope) {
        val handshake = torrentLoader.messageCreator.getHandshakeMessage()

        bytesToSend.send(handshake)
        readChannel.awaitContent()

        val handshakePeer = ByteArray(TorrentLoader.HANDSHAKE_SIZE)
        readChannel.readAvailable(handshakePeer, 0, 68)

        if (!parseHandshake(handshakePeer, torrentLoader.metainfo)) {
            this.scope.cancel()
        }

        if (torrentLoader.somethingDownloaded()) {
            val ownBitfield = torrentLoader.getBitfieldMessage()
            bytesToSend.send(ownBitfield)
        }
        while (scope.isActive) {
            try {
                handleMessages(scope)
            } catch (e: java.lang.Exception) {
                LOG.error("Error while handling messages: ${e.message}")
                if (gotPiece) torrentLoader.putPieceBack(currentPieceIndex)
                this.scope.cancel()
            }
        }

    }

    private suspend fun handleMessages(scope: CoroutineScope) {
        while (scope.isActive) {
            readChannel.awaitContent()
            val lenBytes = readChannel.readNBytes(4)
            val len = lenBytes.toIntFour()

            /*println("received new message")
            println("message length: $len")*/
            if (len == 0) {
                LOG.debug("keep alive received ${++keepAliveCount}")
                continue
            }
            val messageId = readChannel.readNBytes(1).first().toInt()
/*
            println("received code: $messageId")
*/
            if(len > BLOCK_SIZE*2 || len < 0){
                LOG.error("length is broken $len")
                if(gotPiece)
                this.torrentLoader.putPieceBack(currentPieceIndex)
                check(false)
            }
            check(len >= 0)
            /*println("------")*/
            when (messageId) {
                0 -> handleChoking(true)
                1 -> handleChoking(false)
                2 -> handlePeerInterest(true)
                3 -> handlePeerInterest(false)
                4 -> handleHave()
                5 -> handleBitfield(len)
                6 -> handlePeerBlockRequest()
                7 -> handleBlock(len)
                8 -> readChannel.readNBytes(12)//ignore cancel request
                9 -> readChannel.readNBytes(2)//ignore port message
                else -> LOG.error("error got illegal message id")
            }
        }
        LOG.info("closing socket")
        socket.close()
    }

    private suspend inline fun sendBlockRequests(count: Int) {
/*
        println("sending block requests: ")
*/
        (0 until count).forEach { _ ->
            blockRequestsForCurrentPiece.removeFirstOrNull()?.let {
/*
                println("sending for offset: ${it.first}")
*/
                bytesToSend.send(it.second)
                currentlyWaitingBlockRequests += it
            }
        }
    }


    private suspend fun handlePeerInterest(interested: Boolean) {
        LOG.debug("handle peer interest")
        peerConnection.peerInterested = interested
        if (interested) {
            if (peerConnection.amChoking) {
                peerConnection.amChoking = false
                bytesToSend.send(UNCHOKE_MESSAGE)
            }
        } else {
            if (!peerConnection.amChoking) {
                peerConnection.amChoking = true
                bytesToSend.send(CHOKE_MESSAGE)
            }
        }
    }

    private suspend fun handleChoking(choking: Boolean) {
        LOG.debug("changing choking to: $choking")
        peerConnection.peerChoking = choking
        if (choking) {
            blockRequestsForCurrentPiece += currentlyWaitingBlockRequests
            currentlyWaitingBlockRequests.clear()
            gotPiece = false
        } else {
            checkAmInterested()
        }
    }

    private suspend fun handlePeerBlockRequest() {
        val pieceIndex = readChannel.readNBytes(4).toIntFour()
        if (!torrentLoader.alreadyDownloaded[pieceIndex]) {
            LOG.debug("peer requested piece $pieceIndex that is not downloaded yet")
            return
        }
        val blockOffset = readChannel.readNBytes(4).toIntFour()
        val blockLength = readChannel.readNBytes(4).toIntFour()
        val block = torrentLoader.readBytes(pieceIndex, blockOffset, blockLength)
        val pieceMessage = torrentLoader.messageCreator.getPieceMessage(pieceIndex, blockOffset, block)
        bytesToSend.send(pieceMessage)
    }

    private suspend fun handleBlock(len: Int) {
/*
        println("handling block")
*/
        val blockLength = len - 9
        val pieceIndex = readChannel.readNBytes(4).toIntFour()
        check(pieceIndex == currentPieceIndex)
        val blockOffset = readChannel.readNBytes(4).toIntFour()
        val blockData = readChannel.readNBytes(blockLength)
/*
        println("received block for piece $pieceIndex with offset $blockOffset with blocklength: ${blockData.size}")
*/
        writeToPiece(blockOffset, blockData)
        sendBlockRequests(1)
    }

    private suspend fun writeToPiece(blockOffset: Int, blockData: ByteArray) {
        val hadWaitingRequest = currentlyWaitingBlockRequests.removeIf { it.first == blockOffset }
        if (!hadWaitingRequest) {
            LOG.debug("had no waiting request for block so did not write")
            return
        }
        currentPieceBytes.copyIntoSelf(blockData, blockOffset)
        currentPieceDownloadedBytes += blockData.size

        checkWritePiece()
    }

    private suspend fun checkWritePiece() {
        LOG.info("remaining ${currentlyWaitingBlockRequests.size} and ${blockRequestsForCurrentPiece.size}")
        if(currentlyWaitingBlockRequests.isEmpty() && blockRequestsForCurrentPiece.isEmpty()){
            if (!validatePiece()) {
                torrentLoader.putPieceBack(currentPieceIndex)
                getNewPiece()
                return
            }
            writePiece()
            getNewPiece()
        }
    }

    private fun writePiece() {
        LOG.debug("writing piece $currentPieceIndex to file")
        torrentLoader.writePieceToFile(currentPieceIndex, currentPieceBytes)
    }

    private suspend fun getNewPiece() {
        val idx = torrentLoader.getNewPiece(peerConnection)
        LOG.debug("get new piece")
        if (idx == null) {
            currentPieceIndex
            gotPiece = false
            LOG.debug("no piece available")
            return
        }
        LOG.debug("got piece")
        gotPiece = true
        currentPieceIndex = idx
        currentPieceDownloadedBytes = 0
        blockRequestsForCurrentPiece = torrentLoader.messageCreator.getBlockRequestsForPiece(currentPieceIndex)
        sendBlockRequests(SIMULTANEOUS_REQUESTS)
    }

    private fun validatePiece(): Boolean {

        return if(torrentLoader.metainfo.torrentInfo.pieces.lastIndex == currentPieceIndex) {
            var remainingBytes = torrentLoader.metainfo.torrentInfo.getLength().mod(torrentLoader.metainfo.torrentInfo.pieceLength.toInt())
            if(remainingBytes == 0) remainingBytes = torrentLoader.metainfo.torrentInfo.pieceLength.toInt()

            val bytesToCheck = currentPieceBytes.sliceArray(0 until remainingBytes)
            getSHA1ForText(sha1(bytesToCheck)) == torrentLoader.metainfo.torrentInfo.pieces[currentPieceIndex]
        }else {
            getSHA1ForText(sha1(currentPieceBytes)) == torrentLoader.metainfo.torrentInfo.pieces[currentPieceIndex]
        }
    }

    private suspend fun handleHave() {
        LOG.debug("peer has new piece")
        val pieceIndex = readChannel.readNBytes(4).toIntFour()
        peerConnection.availablePieces[pieceIndex] = true
        if (!gotPiece) {
            checkAmInterested()
        }
    }

    private suspend fun handleBitfield(len: Int) {
        val bitfieldLength = len - 1
        val bitfieldBytes = readChannel.readNBytes(bitfieldLength)
        val bitfieldUBytes = bitfieldBytes.toUByteArray()
        val bitfield = bitfieldUBytes.asBitList()
        check(bitfieldLength * 8 >= peerConnection.availablePieces.size)

        peerConnection.availablePieces.indices.forEach {
            peerConnection.availablePieces[it] = bitfield[it]
        }
        checkAmInterested()
    }

    private suspend fun checkAmInterested() {
        LOG.debug("checking if interested")
        if (torrentLoader.interestedInPeer(peerConnection) || gotPiece) {
            LOG.debug("am interested in peer")

            if (!peerConnection.amInterested) {
                LOG.debug("changing to interested")


                bytesToSend.send(INTERESTED_MESSAGE)
                //bytesToSend.send(UNCHOKE_MESSAGE)
                //peerConnection.amChoking = false
                peerConnection.amInterested = true
                LOG.debug("sent interest message")
            } else {
                LOG.debug("already interested in peer")
            }
            if (peerConnection.peerChoking) {
                LOG.debug("cant request piece because choking")
            } else {
                if (!gotPiece) {
                    getNewPiece()
                }
            }

        } else {
            LOG.debug("am uninterested in peer")
            if (peerConnection.amInterested && !gotPiece) {
                LOG.debug("changing to uninterested as i got no piece")
                bytesToSend.send(UNINTERESTED_MESSAGE)
                peerConnection.amInterested = false
            } else {
                LOG.debug("remaining interested as there is still a piece")
            }

        }
    }

    private val buffer = ByteArray(BLOCK_SIZE*2)

    private suspend inline fun ByteReadChannel.readNBytes(bytes: Int): ByteArray {
        val result = ByteArray(bytes)
        var bytesRead = 0
        var remainingBytes = bytes
        while (bytesRead != bytes){
            val read = this.readAvailable(buffer, 0, remainingBytes)
            if(read== -1){
                throw ClosedReceiveChannelException("read channel closed")
            }
            System.arraycopy(buffer, 0, result, bytesRead, read)
            remainingBytes-=read
            bytesRead+= read
        }
        return result
    }

}
