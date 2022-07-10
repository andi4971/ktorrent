package bencode

import java.nio.file.Files
import java.nio.file.Path

class Bencoder(path: Path) {

    private val bytes: ByteArray
    private val text: String
    private var parseIndex = 0

    init {
        bytes = Files.readAllBytes(path)
        text = bytes.map { it.toInt().toChar() }.joinToString(separator = "")
    }

    private val parsedElements = mutableListOf<ParsedData>()

    fun parse(): List<ParsedData> {
        parseIndex = 0
        while (parseIndex != text.length) {
            parseElement()?.let { parsedElements += it }
        }
        return parsedElements
    }

    private fun parseElement(): ParsedData? {

        return when (getCurr()) {
            'i' -> parseInt()
            'l' -> parseList()
            'd' -> parseDictionary()
            'e' -> null
            else -> parseString()
        }
    }

    private fun parseInt(): LongParsedData {
        parseIndex++
        return LongParsedData(takeFromTextWhile { it != 'e' }.toLong())
            .also { parseIndex++ }

    }

    private fun parseString(): StringParsedData {
        val byteLength = takeFromTextWhile { it != ':' }.toInt()
        val byteString = bytes.sliceArray(++parseIndex until parseIndex + byteLength)
        parseIndex += byteLength
        return StringParsedData(byteString)
    }

    private fun parseDictionary(): DictionaryParsedData {
        parseIndex++
        val dict = mutableMapOf<StringParsedData, ParsedData>()
        while (getCurr() != 'e') {
            val key = parseString()
            val value = parseElement()
            require(value != null)
            dict[key] = value
        }
        parseIndex++
        return DictionaryParsedData(dict)
    }

    private fun parseList(): ListParsedData {
        parseIndex++
        val list = mutableListOf<ParsedData>()
        while (getCurr() != 'e') {
            parseElement()?.let { list += it }
        }
        parseIndex++
        return ListParsedData(list)
    }

    private fun takeFromTextWhile(predicate: (Char) -> Boolean): String {
        return text.drop(parseIndex).takeWhile(predicate)
            .also { parseIndex += it.length }

    }

    private fun getCurr() = text[parseIndex]
}
