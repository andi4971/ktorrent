package tracker

class TrackerRequestData(
    downloaded: Long,
    uploaded: Long,
    remaining: Long,
) {
    val downloaded: String
    val uploaded: String
    val remaining: String
    init {
        this.downloaded = downloaded.toString()
        this.uploaded = uploaded.toString()
        this.remaining = remaining.toString()

    }
}
