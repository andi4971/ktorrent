package torrent

import bencode.BencodeParser
import bencode.ParsedDataExtractor
import bencode.entity.Metainfo
import bytesToHumanReadableSize
import file.validateFile
import org.slf4j.LoggerFactory
import java.io.RandomAccessFile
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists

class TorrentInitializer(private val torrentFile :Path, private val folderPath: Path) {

    private val LOG = LoggerFactory.getLogger(this::class.java)

    private val bencodeParser = BencodeParser()
    private val extractor = ParsedDataExtractor()

    private lateinit var metainfo: Metainfo
    private lateinit var alreadyDownloaded: Array<Boolean>

    fun parseData() {
        var parsedData = bencodeParser.parse(torrentFile)
        metainfo = extractor.extractMetainfo(parsedData.first())
        checkAlreadyDownloadedParts()

    }

    private fun checkAlreadyDownloadedParts() {
        if(metainfo.torrentInfo.files == null) {
            val filePath = folderPath.resolve(metainfo.torrentInfo.name)
            if(!filePath.exists()){
                val file = filePath.createFile().toFile()
                RandomAccessFile(file, "rw").setLength(metainfo.torrentInfo.length!!)
            }
            alreadyDownloaded = validateFile(filePath, metainfo.torrentInfo.pieces, metainfo.torrentInfo.pieceLength.toInt())
        }else{
            //folder stuff
            return
        }
    }

    fun printInfo() {
        val pieceLength = bytesToHumanReadableSize(metainfo.torrentInfo.pieceLength.toDouble())
        val filesize = metainfo.torrentInfo.length?.let { bytesToHumanReadableSize(it.toDouble()) } ?: "folder"
        LOG.info("Parsed        \t${metainfo.torrentInfo.name}")
        LOG.info("Pieces:       \t${metainfo.torrentInfo.pieces.size}")
        LOG.info("piece length  \t${pieceLength}")
        LOG.info("size:         \t$filesize")
        LOG.info("hash:         \t${metainfo.infoHashForText}")
        LOG.info("valid pieces: \t${alreadyDownloaded.count{it}}")

    }

    fun getInitializedData(): Pair<Metainfo,Array<Boolean>> {
        return Pair(metainfo, alreadyDownloaded)
    }

}
