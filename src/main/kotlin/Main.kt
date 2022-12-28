import torrent.TorrentInitializer
import torrent.TorrentLoader
import java.nio.file.Path

fun main(args: Array<String>) {

    val torrentFile = Path.of(args.first())

    val folderPath = Path.of(args[1])
    var initializer = TorrentInitializer(torrentFile, folderPath)

    initializer.parseData()
    var data = initializer.getInitializedData()
    initializer.printInfo()

    val loader = TorrentLoader(data.first, data.second, folderPath)
    loader.start()
}

