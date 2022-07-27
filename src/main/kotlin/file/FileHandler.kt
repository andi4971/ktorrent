package file

import bencode.entity.Metainfo
import java.io.RandomAccessFile
import java.nio.file.Path

class FileHandler(val filepath: Path, val metainfo: Metainfo){
    val file = RandomAccessFile(filepath.toFile(), "rw")

    fun writeBytes(pieceIndex: Int, bytes: ByteArray){
        val offset = pieceIndex*metainfo.torrentInfo.pieceLength
        file.seek(offset)
        if(pieceIndex == metainfo.torrentInfo.pieces.lastIndex){
            val remainingBytes = metainfo.torrentInfo.getLength()%metainfo.torrentInfo.pieceLength
            file.write(bytes.sliceArray(0..remainingBytes.toInt()))
        }else{
            file.write(bytes)
        }
    }

    fun readBytes(pieceIndex: Int, offset: Int, length: Int): ByteArray {
        val piecePosition = pieceIndex*metainfo.torrentInfo.pieceLength+offset
        file.seek(piecePosition)
        val block = ByteArray(length)
        file.read(block, 0, length)
        return block
    }

}
