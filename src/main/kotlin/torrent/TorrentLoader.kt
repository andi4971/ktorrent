package torrent

import bencode.entity.Metainfo
import bencode.entity.PeerInfo
import bencode.entity.TrackerResponse
import file.FileHandler
import getHavingIndices
import getRandomPeerId
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import tracker.TrackerRequestData
import tracker.TrackerService
import java.net.SocketException
import java.nio.file.Path
import kotlin.concurrent.timer
import kotlin.concurrent.timerTask
import kotlin.properties.Delegates
import kotlin.system.exitProcess

class TorrentLoader(val metainfo: Metainfo, val alreadyDownloaded: Array<Boolean>, val folderPath: Path) {

    companion object {
        val peerId = getRandomPeerId()
        const val HANDSHAKE_SIZE = 68
        const val maxConnections = 10
    }

    private val trackerService = TrackerService(metainfo)
    private lateinit var lastResponse: TrackerResponse
    private val peers = mutableListOf<PeerInfo>()
    private var remainingBytes by Delegates.notNull<Long>()
    private var downloaded = 0L
    private var uploaded = 0L
    private val fileHandler: FileHandler
    private val remainingPieces: MutableList<Int>
    val messageCreator = MessageCreator(metainfo)
    val availablePeers = Channel<PeerInfo>(capacity = 100)

    init {
        remainingBytes = metainfo.torrentInfo.getLength()
        val path = folderPath.resolve(metainfo.torrentInfo.name)
        fileHandler = FileHandler(path, metainfo)
        remainingPieces = alreadyDownloaded.mapIndexedNotNull { index, b ->
            if (!b)
                index else null
        }.toMutableList()
    }

    private fun getInfoFromTracker() {
        val requestData = TrackerRequestData(
            downloaded,
            uploaded,
            remainingBytes
        )
        lastResponse = trackerService.getInfoFromTracker(requestData)
    }

    fun start() {
        getInfoFromTracker()
        startConnection()
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private fun startConnection() {
        peers.clear()
        peers += lastResponse.peers


        val peerHandlers = mutableListOf<PeerHandler>()
        runBlocking {
            peers.forEach { availablePeers.send(it) }
            var openConnections = 0

            while (true){
                while (openConnections < maxConnections) {
                    try {
                        launch(newSingleThreadContext("handler")) {
                            println("starting connection to new peer")
                            val handler = PeerHandler(this@TorrentLoader, this)
                            handler.start()
                        }
                        openConnections++
                    } catch (e: Exception) {
                        println("closing handler exception: ${e.message}")
                        openConnections--
                    }
                }
                delay(5000)
            }
        }
    }

    fun getPeerInfo(): PeerInfo {
        return peers.removeFirst()
    }

    fun getBitfieldMessage(): ByteArray {
        return messageCreator.getBitfieldMessage(alreadyDownloaded)
    }

    fun somethingDownloaded(): Boolean {
        return alreadyDownloaded.any { it }
    }


    fun getNewPiece(peer: PeerConnection): Int? {
        val peerHavingIndices = peer.availablePieces.getHavingIndices().toSet()
        val intersection = remainingPieces intersect peerHavingIndices
        if (intersection.isEmpty()) return null
        val chosenIndex = intersection.random()
        remainingPieces.remove(chosenIndex)
        return chosenIndex
    }

    fun interestedInPeer(peer: PeerConnection): Boolean {
        val peerHavingIndices = peer.availablePieces.getHavingIndices().toSet()
        return (remainingPieces intersect peerHavingIndices).isNotEmpty()
    }

    fun writePieceToFile(pieceIndex: Int, bytes: ByteArray) {
        fileHandler.writeBytes(pieceIndex, bytes)
        alreadyDownloaded[pieceIndex] = true
    }

    fun putPieceBack(idx: Int) {
        remainingPieces.add(idx)
    }

    fun readBytes(pieceIndex: Int, blockBegin: Int, blockLength: Int): ByteArray {
        return fileHandler.readBytes(pieceIndex, blockBegin, blockLength)
    }

}
