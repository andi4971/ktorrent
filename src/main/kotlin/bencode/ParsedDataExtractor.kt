package bencode

import bencode.entity.*
import getSHA1ForText
import getSHA1ForUrl
import io.ktor.util.*
import toInt
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.io.path.Path

class ParsedDataExtractor {

    private val hexFormat = HexFormat.of()

    fun extractMetainfo(parsedData: ParsedData): Metainfo {
        val dict = parsedData as DictionaryParsedData
        val announceUrl = dict.getValue<StringParsedData>("announce")
        val announceList = dict.getValueOrNull<ListParsedData>("announce-list")
            ?.getValues<ListParsedData>().orEmpty()
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

        val sha1 = sha1(info.rawData)
        return Metainfo(
            announceUrl.text,
            announceList,
            comment?.text,
            createdBy?.text,
            encoding?.text,
            torrentInfo,
            sha1,
            getSHA1ForUrl(sha1),
            getSHA1ForText(sha1)
        )
    }

    private fun parsePeers(value: ListParsedData): List<PeerInfo> {
        return value.value
            .map { it as DictionaryParsedData }
            .map {
                val peerId = it.getValueOrNull<StringParsedData>("peer id")?.text
                val ip = it.getValue<StringParsedData>("ip").text
                val port = it.getValue<LongParsedData>("port").value.toInt()
                PeerInfo(
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
        val complete = dict.getValueOrNull<LongParsedData>("complete")?.value ?: 0
        val incomplete = dict.getValueOrNull<LongParsedData>("incomplete")?.value ?: 0
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

    private fun parsePeers(value: StringParsedData): List<PeerInfo> {

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
                PeerInfo(ip,ip,port)
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


}
