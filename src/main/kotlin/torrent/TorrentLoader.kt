package torrent

import bencode.entity.Metainfo
import tracker.TrackerService

class TorrentLoader(private val metaInfo: Metainfo) {
    private val trackerService = TrackerService(metaInfo)

    fun getInfoFromTracker() {
        trackerService.getInfoFromTracker()
    }
}
