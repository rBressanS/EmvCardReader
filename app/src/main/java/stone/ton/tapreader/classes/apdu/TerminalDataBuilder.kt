package stone.ton.tapreader.classes.apdu

class TerminalDataBuilder {
    val terminalTags = mapOf<String, String>("9F 66" to "B7E0C800",
    "9F 02" to "000000000001",
        "9F 03" to "000000000000",
        "9F 1A" to "0076",
        "95" to "0000000000",
        "5F 2A" to "0986",
        "9A" to "010101",
        "9C" to "00",
        "9F 37" to "01234567")
}