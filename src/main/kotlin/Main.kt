import bencode.Bencoder
import bencode.ParsedDataExtractor
import file.validateFile
import torrent.MessageCreator
import torrent.TorrentLoader
import java.io.RandomAccessFile
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileAttribute
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.fileStore

fun main(args: Array<String>) {

    val trackerFile = Path.of(args.first())

    val bencoder = Bencoder()
    val parsedData = bencoder.parse(trackerFile)
    val extractor = ParsedDataExtractor()
    val metainfo = extractor.extractMetainfo(parsedData.first())
    val pieceLength = bytesToHumanReadableSize(metainfo.torrentInfo.pieceLength.toDouble())
    val filesize = metainfo.torrentInfo.length?.let { bytesToHumanReadableSize(it.toDouble()) } ?: "folder"
    println("Parsed '${metainfo.torrentInfo.name}' with ${metainfo.torrentInfo.pieces.size} pieces,  piece length '${pieceLength}', size: '$filesize', hash: '${metainfo.infoHashForText}'")

    val alreadyDownloaded: Array<Boolean>
    val folderPath = Path.of(args[1])
    if(metainfo.torrentInfo.files == null) {
        val filePath = folderPath.resolve(metainfo.torrentInfo.name)
        if(!filePath.exists()){
            val view = Files.getFileAttributeView(filePath, BasicFileAttributeView::class.java)
            val file = filePath.createFile().toFile()
            RandomAccessFile(file, "rw").setLength(metainfo.torrentInfo.length!!)
        }
        alreadyDownloaded = validateFile(filePath, metainfo.torrentInfo.pieces, metainfo.torrentInfo.pieceLength.toInt())
    }else{
        //folder stuff
        return
    }

    val loader = TorrentLoader(metainfo, alreadyDownloaded, folderPath)
    loader.start()
}
