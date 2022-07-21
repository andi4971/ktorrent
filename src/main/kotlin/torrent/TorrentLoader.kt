package torrent

import bencode.entity.Metainfo
import bencode.entity.PeerInfo
import bencode.entity.TrackerResponse
import getNBytes
import getRandomPeerId
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import tracker.TrackerRequestData
import tracker.TrackerService
import java.nio.ByteBuffer
import kotlin.properties.Delegates
import kotlin.system.exitProcess

class TorrentLoader(private val metainfo: Metainfo) {

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
    private val messageCreator = MessageCreator(metainfo)

    init {
        remainingBytes = metainfo.torrentInfo.length ?: metainfo.torrentInfo.files!!.sumOf { it.length }
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
        val handshakeMessage = messageCreator.getHandshakeMessage()
        startConnection(handshakeMessage)
    }

    fun startConnection(handshake: ByteArray) {
        val peers = lastResponse.peers.toMutableList()


        runBlocking {
            while (true) {
                var socket: Socket? = null
                while (socket == null) {
                    socket = connectToPeer(peers.removeFirst())
                }

                val receiveChannel = socket.openReadChannel()
                val sendChannel = socket.openWriteChannel(autoFlush = true)

                sendChannel.writeAvailable(ByteBuffer.wrap(handshake))
                receiveChannel.awaitContent()

                val readBuffer = ByteBuffer.allocate(10000)
                while (readBuffer.position() < HANDSHAKE_SIZE){
                    receiveChannel.readAvailable(readBuffer)
                }
                readBuffer.flip()
                var result = readBuffer.getNBytes(HANDSHAKE_SIZE)
                if(!parseHandshake(result)) continue

                result = readBuffer.getNBytes(readBuffer.slice().remaining())
                exitProcess(0)
            }
        }

    }

    private fun parseHandshake(result: ByteArray): Boolean {
        return try {
            val handshakeResponse =
                ByteParser.parseHandshake(result.sliceArray(0 until HANDSHAKE_SIZE), metainfo.infoHashForText)
            println("Successful handshake with peerid: ${handshakeResponse.peerId}  reserved bits: ${handshakeResponse.reservedBits}")
            true
        } catch (e: java.lang.IllegalStateException) {
            println("failure while validating handshake: ${e.message}")
            false
        }
    }


    private suspend fun connectToPeer(peer: PeerInfo): Socket? {
        return try {
            aSocket(selectorManager).tcp().connect(peer.ip, peer.port) {
                socketTimeout = 2000
                this

            }
        } catch (e: java.lang.Exception) {
            println("Could not connect to peer ${peer.ip}:${peer.port} because: ${e.message}")
            null
        }
    }

    private fun ByteBuffer.readAvailable(): ByteArray {
        val bytes = ByteArray(this.remaining())
        this.get(bytes)
        return bytes
    }

    private fun ByteBuffer.readBytes(): ByteArray {
        val bytes = ByteArray(this.remaining())
        this.get(bytes)
        return bytes
    }
}
