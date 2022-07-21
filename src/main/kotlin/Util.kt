import torrent.TorrentLoader
import java.nio.ByteBuffer
const val bittorrentProtocol =  "BitTorrent protocol"
fun bytesToHumanReadableSize(bytes: Double) = when {
    bytes >= 1 shl 30 -> "%.1f GB".format(bytes / (1 shl 30))
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes / (1 shl 20))
    bytes >= 1 shl 10 -> "%.0f kB".format(bytes / (1 shl 10))
    else -> "$bytes bytes"
}

fun getRandomPeerId(): String {
    val start = "-KK001-"
    val allowedChars = ('0'..'9')
     return start + (start.length until 20)
        .map { allowedChars.random() }
        .joinToString("")
}

fun ByteArray.toInt(): Int {
    check(this.size == 2)
    val low  = this[1].toUByte()
    val high = this[0].toUByte()

   return (high.toInt() shl 8) or low.toInt()
}

fun intToBytes(i: Int): ByteArray =
    ByteBuffer.allocate(Int.SIZE_BYTES).putInt(i).array()

fun ByteBuffer.getNBytes(bytes: Int): ByteArray {
    val result = ByteArray(bytes)
    this.get(result, 0,bytes)
    this.position(bytes)
    return result
}

fun Byte.toBitString(): String {
    return this.toString(2).padStart(8, '0')
}
