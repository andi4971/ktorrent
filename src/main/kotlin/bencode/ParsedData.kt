package bencode

import java.nio.charset.Charset

interface ParsedData {
}

data class LongParsedData(val value: Long): ParsedData

class StringParsedData(val value: ByteArray): ParsedData{
    val text: String = value.toString(Charset.forName("UTF-8"))
}

class DictionaryParsedData(val value: Map<StringParsedData, ParsedData>): ParsedData{
    fun<T: ParsedData> getValue(key: String): T {
        val mapKey = value.keys.first { it.text == key}
        return value.getValue(mapKey) as T
    }
    fun<T: ParsedData> getValueOrNull(key: String): T? {
        val mapKey = value.keys.firstOrNull { it.text == key}
        return mapKey?.let { value.getValue(mapKey) as T}
    }
    fun existsKey(key: String): Boolean {
        return value.keys.firstOrNull { it.text == key } != null
    }

}

class ListParsedData(val value: List<ParsedData>): ParsedData {
    fun <T: ParsedData>getValues(): List<T> {
        return value.map { it as T }
    }
}


