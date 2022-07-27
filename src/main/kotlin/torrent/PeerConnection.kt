package torrent

import bencode.entity.PeerInfo

class PeerConnection(private val peerInfo: PeerInfo, numberPieces: Int) {
    var amChoking = true
    var amInterested = false
    var peerChoking = true
    var peerInterested = false
    val availablePieces = Array(numberPieces) {false}
}
