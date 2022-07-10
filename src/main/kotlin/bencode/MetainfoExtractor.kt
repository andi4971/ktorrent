package bencode

class MetainfoExtractor(val parsedData: ParsedData) {

    fun extractMetainfo(): Metainfo {
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
        val pieces = info.getValue<StringParsedData>("pieces")
        val length = info.getValueOrNull<LongParsedData>("length")
        val files = info.getValueOrNull<DictionaryParsedData>("files")

        val torrentInfo = TorrentInfo(
            name = name.text,
            pieceLength = pieceLength.value,
            pieces = pieces,
            length = length?.value,
            files = files
        )

        return Metainfo(
            announceUrl.text,
            announceList,
            comment?.text,
            createdBy?.text,
            encoding?.text,
            torrentInfo
        )
    }


}
