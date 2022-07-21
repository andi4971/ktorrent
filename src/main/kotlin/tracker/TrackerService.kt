package tracker

import bencode.Bencoder
import bencode.ParsedDataExtractor
import bencode.entity.Metainfo
import bencode.entity.TrackerResponse
import feign.Feign
import feign.okhttp.OkHttpClient
import getRandomPeerId
import torrent.TorrentLoader.Companion.peerId
import tracker.TrackerRequestFields.DOWNLOADED
import tracker.TrackerRequestFields.INFO_HASH
import tracker.TrackerRequestFields.LEFT
import tracker.TrackerRequestFields.PEER_ID
import tracker.TrackerRequestFields.PORT
import tracker.TrackerRequestFields.UPLOADED
import java.net.URLEncoder

class TrackerService(private val metainfo: Metainfo) {

    private var port = 6881
    private val bencoder = Bencoder()
    private val parsedDataExtractor = ParsedDataExtractor()
    private val client: TrackerApi = Feign.builder()
        .client(OkHttpClient())
        .target(TrackerApi::class.java, metainfo.announceURL)

    fun getInfoFromTracker(requestData: TrackerRequestData): TrackerResponse {
        val queryMap = mutableMapOf<String, String>()
        queryMap[INFO_HASH]=metainfo.infoHashForUrl.urlEncode()
        queryMap[PEER_ID]=peerId.urlEncode()
        queryMap[PORT]=port.toString()
        queryMap[DOWNLOADED]=requestData.downloaded
        queryMap[UPLOADED]=requestData.uploaded
        queryMap[LEFT]=requestData.remaining
        println("Encoded info_hash: ${queryMap[INFO_HASH]}")
        val response = client.getTrackerInfo(queryMap)
        val parsedResponse = bencoder.parse(response).first()
        return parsedDataExtractor.extractTrackerResponse(parsedResponse)
    }

    private fun String.urlEncode(): String {
        return URLEncoder.encode(this, Charsets.ISO_8859_1)
    }
}
