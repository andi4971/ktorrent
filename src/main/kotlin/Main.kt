import bencode.Bencoder
import bencode.ParsedDataExtractor
import torrent.TorrentLoader
import java.nio.file.Path

fun main(args: Array<String>) {

    val path = Path.of(args.first())

    val bencoder = Bencoder()
    val parsedData = bencoder.parse(path)
    val extractor = ParsedDataExtractor()
    val metainfo = extractor.extractMetainfo(parsedData.first())
    val pieceLength =bytesToHumanReadableSize(metainfo.torrentInfo.pieceLength.toDouble())
    val filesize = metainfo.torrentInfo.length?.let { bytesToHumanReadableSize(it.toDouble()) } ?: "folder"
    println("Parsed '${metainfo.torrentInfo.name}' with piece length '${pieceLength}', size: '$filesize', hash: '${metainfo.infoHashForText}'")

    val loader = TorrentLoader(metainfo)
    loader.getInfoFromTracker()
}
