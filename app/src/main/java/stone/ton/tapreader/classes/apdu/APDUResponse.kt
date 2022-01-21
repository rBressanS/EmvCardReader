package stone.ton.tapreader.classes.apdu

class APDUResponse(fullData: ByteArray) {
    var sw1: Byte = 0
    var sw2: Byte = 0
    var data: ByteArray = ByteArray(0)

    init {
        sw2 = fullData.dropLast(1)[0]
        sw1 = fullData.dropLast(1)[0]
        data = fullData.copyOf()
    }
}