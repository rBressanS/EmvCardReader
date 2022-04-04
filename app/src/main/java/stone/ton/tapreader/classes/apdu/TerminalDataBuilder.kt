package stone.ton.tapreader.classes.apdu

class TerminalDataBuilder {
    val terminalTags = mapOf(
        "9F 66" to "B7E0C800".decodeHex(),
        "9F 02" to "000000000001".decodeHex(),
        "9F 03" to "000000000000".decodeHex(),
        "9F 1A" to "0076".decodeHex(),
        //"9F 1A" to "0826".decodeHex(),
        "95" to "0000000000".decodeHex(),
        "5F 2A" to "0986".decodeHex(),
        //"5F 2A" to "0826".decodeHex(),
        "9A" to "010101".decodeHex(),
        "9C" to "00".decodeHex(),
        "9F 37" to "01234567".decodeHex()
    )

    fun String.decodeHex(): ByteArray {
        check((this.replace(" ", "").length % 2) == 0) { "Must have an even length" }

        return replace(" ", "").chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}