package bencode

import bencode.entity.*
import toInt
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat
import javax.sound.midi.Track
import kotlin.io.path.Path

class ParsedDataExtractor {

    private val hexFormat = HexFormat.of()

    fun extractMetainfo(parsedData: ParsedData): Metainfo {
        val dict = parsedData as DictionaryParsedData
        val announceUrl = dict.getValue<StringParsedData>("announce")
        val announceList = dict.getValue<ListParsedData>("announce-list")
            .getValues<ListParsedData>()
            .flatMap { it.getValues<StringParsedData>() }
            .map { it.text }
        val comment = dict.getValueOrNull<StringParsedData>("comment")
        val createdBy = dict.getValueOrNull<StringParsedData>("createdBy")
        val encoding = dict.getValueOrNull<StringParsedData>("encoding")

        val info = dict.getValue<DictionaryParsedData>("info")
        val name = info.getValue<StringParsedData>("name")
        val pieceLength = info.getValue<LongParsedData>("piece length")
        val pieces = extractPieces(info.getValue("pieces"))
        val length = info.getValueOrNull<LongParsedData>("length")
        val files = info.getValueOrNull<ListParsedData>("files")?.let { extractFiles(it) }

        val torrentInfo = TorrentInfo(
            name = name.text,
            pieceLength = pieceLength.value,
            pieces = pieces,
            length = length?.value,
            files = files
        )

        val sha1 = getSHA1(info.rawData)
        return Metainfo(
            announceUrl.text,
            announceList,
            comment?.text,
            createdBy?.text,
            encoding?.text,
            torrentInfo,
            getSHA1ForUrl(sha1),
            getSHA1ForText(sha1)
        )
    }

    private fun parsePeers(value: ListParsedData): List<Peer> {
        return value.value
            .map { it as DictionaryParsedData }
            .map {
                val peerId = it.getValueOrNull<StringParsedData>("peer id")?.text
                val ip = it.getValue<StringParsedData>("ip").text
                val port = it.getValue<LongParsedData>("port").value.toInt()
                Peer(
                    peerId ?: (ip + port.toString()),
                    ip,
                    port
                )
            }
    }

    fun extractTrackerResponse(parsedData: ParsedData): TrackerResponse {
        val dict = parsedData as DictionaryParsedData
        val failureReason = dict.getValueOrNull<StringParsedData>("failure-reason")?.text
        val warningMessage = dict.getValueOrNull<StringParsedData>("warning-message")?.text
        val interval = dict.getValue<LongParsedData>("interval").value
        val minInterval = dict.getValueOrNull<LongParsedData>("min interval")?.value
        val trackerId = dict.getValueOrNull<StringParsedData>("tracker id")?.text
        val complete = dict.getValue<LongParsedData>("complete").value
        val incomplete = dict.getValue<LongParsedData>("incomplete").value
        val peers = if (dict.isValueOfType("peers", ListParsedData::class)) {
            parsePeers(dict.getValue<ListParsedData>("peers"))
        } else {
            parsePeers(dict.getValue<StringParsedData>("peers"))
        }

        return TrackerResponse(
            failureReason,
            warningMessage,
            interval,
            minInterval,
            trackerId,
            complete,
            incomplete,
            peers
        )
    }

    private fun parsePeers(value: StringParsedData): List<Peer> {

        return value.value
            .toList()
            .chunked(6)
            .map { bytes ->
                val ip = bytes.take(4)
                    .map { it.toUByte().toInt() }
                    .joinToString(separator = ".")
                val port = bytes
                .takeLast(2)
                .toByteArray().toInt()
                Peer(ip,ip,port)
            }
    }


    private fun extractFiles(files: ListParsedData): List<TorrentFile> {
        return files.value
            .map { it as DictionaryParsedData }
            .map { dict ->
                val length = dict.getValue<LongParsedData>("length").value
                val path = dict.getValue<ListParsedData>("path").value
                    .map { (it as StringParsedData).text }
                    .toMutableList()
                    .let {
                        val first = it.removeFirst()
                        Path(first, *(it.toTypedArray()))
                    }
                TorrentFile(
                    path,
                    length
                )
            }
    }

    private fun extractPieces(data: StringParsedData): List<String> {
        return data.value
            .toList()
            .chunked(20)
            .map { hexFormat.formatHex(it.toByteArray()) }
    }

    private fun getSHA1(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(data)
    }

    private fun getSHA1ForUrl(sha1: ByteArray): String {
        return String(sha1, Charsets.ISO_8859_1)
    }

    private fun getSHA1ForText(sha1: ByteArray): String {
        return hexFormat.formatHex(sha1)
    }


}
