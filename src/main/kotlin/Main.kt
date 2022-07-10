import bencode.Bencoder
import bencode.MetainfoExtractor
import java.nio.file.Path

fun main(args: Array<String>) {

    val path = Path.of(args.first())

    val bencoder = Bencoder(path)
    val parsedData = bencoder.parse()
    val extractor = MetainfoExtractor(parsedData.first())
    val metainfo = extractor.extractMetainfo()
    println("Parsed '${metainfo.torrentInfo.name}'")
}
