package stone.ton.tapreader.models.apdu

import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvParser

class APDUResponse(fullData: ByteArray, val timeTakenInNanoSeconds: Long) {
    var sw1: Byte = 0
    var sw2: Byte = 0
    var data: ByteArray = ByteArray(0)

    init {
        var response = fullData
        sw2 = response.last()
        response = response.dropLast(1).toByteArray()
        sw1 = response.last()
        response = response.dropLast(1).toByteArray()
        data = response.copyOf()
    }

    fun wasSuccessful(): Boolean {
        return sw1 == 0x90.toByte() && sw2 == 0.toByte()
    }

    fun getParsedData(): BerTlv {
        return parser.parseConstructed(data)
    }

    companion object {
        private val parser = BerTlvParser()
    }
}