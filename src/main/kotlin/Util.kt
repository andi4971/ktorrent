import bencode.entity.PeerInfo
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import torrent.MessageCreator.Companion.BLOCK_SIZE
import java.nio.ByteBuffer
import java.util.HexFormat

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

fun ByteArray.toIntFour(): Int{
    check(this.size == 4)
    return ByteBuffer.wrap(this).int
}

fun Int.toByteArray(): ByteArray =
    ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()

fun ByteBuffer.getNBytes(bytes: Int): ByteArray {
    val result = ByteArray(bytes)
    this.get(result, 0,bytes)
    this.position(bytes)
    return result
}

fun Byte.toBitString(): String {
    return this.toString(2).padStart(8, '0')
}
fun UByte.toBitString(): String {
    return this.toString(2).padStart(8, '0')
}

@OptIn(ExperimentalUnsignedTypes::class)
fun UByteArray.asBitList(): List<Boolean> {
   return this.joinToString(separator = "") { it.toBitString() }.map { it == '1' }
}

fun getSHA1ForUrl(sha1: ByteArray): String {
    return String(sha1, Charsets.ISO_8859_1)
}
private val hexFormat = HexFormat.of()

fun getSHA1ForText(sha1: ByteArray): String {
    return hexFormat.formatHex(sha1)
}

suspend inline fun ByteWriteChannel.writeAvailable(bytes: ByteArray){
    this.writeAvailable(ByteBuffer.wrap(bytes))
}

fun Array<Boolean>.getNeededIndices(): List<Int>{
    return this.mapIndexedNotNull {index, b ->  if(!b) index else null }
}

fun Array<Boolean>.getHavingIndices(): List<Int>{
    return this.mapIndexedNotNull {index, b ->  if(b) index else null }
}

fun ByteArray.copyIntoSelf(other: ByteArray, destPos: Int) {
    System.arraycopy(other, 0, this, destPos, other.size)
}

private val selectorManager = SelectorManager(Dispatchers.IO)

suspend fun connectToPeer(peer: PeerInfo): Socket? {
    return try {
        aSocket(selectorManager).tcp().connect(peer.ip, peer.port)
    } catch (e: java.lang.Exception) {
        println("Could not connect to peer ${peer.ip}:${peer.port} because: ${e.message}")
        null
    }
}
