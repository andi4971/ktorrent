package torrent

import bencode.entity.Metainfo
import bencode.entity.PeerInfo
import bencode.entity.TrackerResponse
import file.FileHandler
import getHavingIndices
import getRandomPeerId
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import tracker.TrackerRequestData
import tracker.TrackerService
import java.nio.file.Path
import kotlin.properties.Delegates
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

class TorrentLoader(val metainfo: Metainfo, val alreadyDownloaded: Array<Boolean>, val folderPath: Path) {

    private val LOG = LoggerFactory.getLogger(this::class.java)

    companion object {
        val peerId = getRandomPeerId()
        const val HANDSHAKE_SIZE = 68
        const val maxConnections = 3
    }

    private val trackerService = TrackerService(metainfo)
    private lateinit var lastResponse: TrackerResponse
    private val peers = mutableSetOf<PeerInfo>()
    private val sentPeers = mutableSetOf<PeerInfo>()
    private var remainingBytes by Delegates.notNull<Long>()
    private var downloaded = 0L
    private var uploaded = 0L
    private val fileHandler: FileHandler
    private val remainingPieces: MutableList<Int>
    val messageCreator = MessageCreator(metainfo)
    val availablePeers = Channel<PeerInfo>(capacity = 100)
    val peerHandlers = mutableListOf<PeerHandler>()
    lateinit var scope: CoroutineScope
    var startTime by Delegates.notNull<Long>()
    private var pieceCount by Delegates.notNull<Int>()
    private var downloadedPieceCount = 0
    private var lastTrackerRequestTime by Delegates.notNull<Long>()

    init {
        downloadedPieceCount = alreadyDownloaded.count { it }
        remainingBytes =
            metainfo.torrentInfo.getLength() - downloadedPieceCount * metainfo.torrentInfo.pieceLength
        val path = folderPath.resolve(metainfo.torrentInfo.name)
        fileHandler = FileHandler(path, metainfo)
        remainingPieces = alreadyDownloaded.mapIndexedNotNull { index, b ->
            if (!b)
                index else null
        }.toMutableList()
        pieceCount = alreadyDownloaded.count()
    }

    private fun getInfoFromTracker() {
        val requestData = TrackerRequestData(
            downloaded,
            uploaded,
            remainingBytes
        )
        LOG.info("Communicating with tracker to get peers")
        lastResponse = trackerService.getInfoFromTracker(requestData)
        lastTrackerRequestTime = System.currentTimeMillis()
        LOG.info("Received ${lastResponse.peers.count()} peers from tracker")
        peers += lastResponse.peers
    }

    fun start() {
        getInfoFromTracker()
        startTime = System.currentTimeMillis()
        startConnections()

    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private fun startConnections() {


        runBlocking {
            scope = this
            peers.forEach {
                availablePeers.send(it)
                sentPeers.add(it)
            }
            var openConnections = 0
            var jobs = mutableListOf<Job>()
            while (true) {
                while (openConnections < maxConnections) {
                    try {
                        var job = launch(newSingleThreadContext("handler $openConnections")) {
                            LOG.debug("starting connection to new peer")
                            val handler = PeerHandler(this@TorrentLoader, this@launch)
                            handler.start()
                        }
                        jobs += job
                        LOG.info("Open connections: ${++openConnections}")
                    } catch (e: Exception) {
                        LOG.error("closing handler exception: ${e.message}")
                        LOG.info("Open connections: ${--openConnections}")
                    }
                }
                var deadJobs = jobs.filter { it.isCancelled || it.isCompleted }
                if (deadJobs.isNotEmpty()) {
                    jobs -= deadJobs
                    openConnections -= deadJobs.size
                    LOG.info("Removing ${deadJobs.size} dead jobs")
                }

                delay(3000)
                var timeSinceLastRequest = System.currentTimeMillis().milliseconds - lastTrackerRequestTime.milliseconds
                var newRequestPossible = timeSinceLastRequest.inWholeSeconds >= lastResponse.interval

                if(!newRequestPossible ){
                    LOG.info("No new request to tracker possible yet. Remaining time: ${lastResponse.interval - timeSinceLastRequest.inWholeSeconds} seconds")
                }

                if (availablePeers.isEmpty && newRequestPossible) {
                    getInfoFromTracker()
                    peers
                        .filter { it !in sentPeers }
                        .forEach {
                            availablePeers.send(it)
                            sentPeers.add(it)
                        }
                }
            }
        }
    }

    fun getBitfieldMessage(): ByteArray {
        return messageCreator.getBitfieldMessage(alreadyDownloaded)
    }

    fun somethingDownloaded(): Boolean {
        return alreadyDownloaded.any { it }
    }


    fun getNewPiece(peer: PeerConnection): Int? {
        synchronized(remainingPieces) {
            val peerHavingIndices = peer.availablePieces.getHavingIndices().toSet()
            val intersection = remainingPieces intersect peerHavingIndices
            if (intersection.isEmpty()) return null
            val chosenIndex = intersection.random()
            remainingPieces.remove(chosenIndex)
            return chosenIndex
        }
    }

    fun interestedInPeer(peer: PeerConnection): Boolean {
        val peerHavingIndices = peer.availablePieces.getHavingIndices().toSet()
        return (remainingPieces intersect peerHavingIndices).isNotEmpty()
    }

    fun writePieceToFile(pieceIndex: Int, bytes: ByteArray) {
        fileHandler.writeBytes(pieceIndex, bytes)
        alreadyDownloaded[pieceIndex] = true
        sendHavePieceMessages(pieceIndex)
        synchronized(this) {
            downloaded += bytes.size
            downloadedPieceCount++
        }
        LOG.info("Downloaded $downloadedPieceCount of $pieceCount")
        if (downloadedPieceCount == pieceCount) {
            var finishedTime = System.currentTimeMillis()
            var duration = finishedTime.milliseconds - startTime.milliseconds
            LOG.info("Finished downloading torrent. Took seconds: ${duration.inWholeSeconds}")
        }
    }

    fun putPieceBack(idx: Int) {
        synchronized(remainingPieces) {
            remainingPieces.add(idx)
        }
    }

    fun readBytes(pieceIndex: Int, blockBegin: Int, blockLength: Int): ByteArray {
        return fileHandler.readBytes(pieceIndex, blockBegin, blockLength)
            .also { uploaded += it.size }
    }

    private fun sendHavePieceMessages(pieceIndex: Int) {
        this.scope.launch {
            val havePieceMessage = messageCreator.getHavePieceMessage(pieceIndex)
            peerHandlers.forEach {

                if (!it.peerConnection.availablePieces[pieceIndex]) {
                    it.bytesToSend.send(havePieceMessage)
                }
            }
        }
    }

}
