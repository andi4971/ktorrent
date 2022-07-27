package file

import getSHA1ForText
import io.ktor.util.*
import java.nio.file.Path
import kotlin.io.path.inputStream

fun validateFile(path: Path, pieces: List<String>, pieceLength: Int): Array<Boolean> {
    val hashes = Array(pieces.size) { false }

    val input = path.inputStream()
    for (i in pieces.indices) {
        val bytes = input.readNBytes(pieceLength)
        if(bytes.isEmpty()) break
        val hash = getSHA1ForText(sha1(bytes))
        if (pieces[i] == hash) {
            hashes[i] = true
        }
        if(i%10 == 0){
            println("validated $i pieces of ${pieces.size}")
        }
    }
    return hashes
}
