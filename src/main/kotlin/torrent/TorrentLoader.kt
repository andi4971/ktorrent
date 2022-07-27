package torrent

import bencode.entity.Metainfo
import bencode.entity.PeerInfo
import bencode.entity.TrackerResponse
import file.FileHandler
import getHavingIndices
import getRandomPeerId
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import tracker.TrackerRequestData
import tracker.TrackerService
import java.nio.file.Path
import kotlin.properties.Delegates
import kotlin.system.exitProcess

class TorrentLoader(val metainfo: Metainfo, val alreadyDownloaded: Array<Boolean>, val folderPath: Path) {

    companion object {
        val peerId = getRandomPeerId()
        const val HANDSHAKE_SIZE = 68
    }

    private val trackerService = TrackerService(metainfo)
    private lateinit var lastResponse: TrackerResponse
    private var remainingBytes by Delegates.notNull<Long>()
    private var downloaded = 0L
    private var uploaded = 0L
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val fileHandler: FileHandler
    private val remainingPieces: MutableList<Int>
    val messageCreator = MessageCreator(metainfo)

    init {
        remainingBytes = metainfo.torrentInfo.getLength()
        val path = folderPath.resolve(metainfo.torrentInfo.name)
        fileHandler = FileHandler(path, metainfo)
        remainingPieces = alreadyDownloaded.mapIndexedNotNull { index, b -> if(!b)
         index else null
        } .toMutableList()
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

    private fun startConnection() {
        val peers = lastResponse.peers.toMutableList()

        val peerHandlers = mutableListOf<PeerHandler>()

        runBlocking {
            while (true) {
                var socket: Socket? = null
                var peer: PeerInfo? = null
                while (socket == null) {
                    peer = peers.removeFirst()
                    socket = connectToPeer(peer)
                }
                if(peer == null){
                    error("no peers left :(")
                }
                val handler = PeerHandler(this@TorrentLoader, peer,this.coroutineContext,socket)
                peerHandlers.add(handler)
                handler.start()
                exitProcess(0)
            }
        }
    }

    fun getBitfieldMessage(): ByteArray {
        return messageCreator.getBitfieldMessage(alreadyDownloaded)
    }

    fun somethingDownloaded(): Boolean {
        return alreadyDownloaded.any{it}
    }

    private suspend fun connectToPeer(peer: PeerInfo): Socket? {
        return try {
            aSocket(selectorManager).tcp().connect(peer.ip, peer.port) {
                socketTimeout = 2000
            }
        } catch (e: java.lang.Exception) {
            println("Could not connect to peer ${peer.ip}:${peer.port} because: ${e.message}")
            null
        }
    }

    fun getNewPiece(peer: PeerConnection): Int? {
        val peerHavingIndices =  peer.availablePieces.getHavingIndices().toSet()
        val intersection = remainingPieces intersect peerHavingIndices
        if(intersection.isEmpty()) return null
        val chosenIndex = intersection.random()
        remainingPieces.remove(chosenIndex)
        return chosenIndex
    }

    fun writePieceToFile(pieceIndex: Int, bytes: ByteArray){
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
