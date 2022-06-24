package stone.ton.tapreader.models.emv

class AFLEntry(fullAflEntry: ByteArray) {

    var sfi: Byte = (fullAflEntry[0].toInt() shr 3).toByte()
    var start: Byte = fullAflEntry[1]
    var end: Byte = fullAflEntry[2]
    var isSigned: Boolean = fullAflEntry[3] > 0


    fun getSfiForP2(): Byte {
        return ((sfi.toInt() shl 3) + 4.toByte()).toByte()
    }
}
