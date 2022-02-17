package stone.ton.tapreader.classes.apdu

import kotlin.experimental.and

class DOL(fullDol: ByteArray) {

    var requiredTags: MutableMap<String, Int>
    init {
        var remainingData = fullDol
        requiredTags = HashMap()
        while(remainingData.isNotEmpty()){
            var returnable = getTag(remainingData)
            remainingData = returnable["REMAINING"]!!
            val tag = returnable["TAG"]!!
            returnable = getLength(remainingData)
            remainingData = returnable["REMAINING"]!!
            val length = returnable["LENGTH"]!!
            requiredTags[byte2Hex(tag)] = length[0].toInt()
        }
    }

    private fun byte2Hex(input: ByteArray): String {
        /*!
        This method converts bytes to strings of hex
        */
        val result = StringBuilder()
        for (inputbyte in input) {
            result.append(String.format("%02X" + " ", inputbyte))
        }
        return result.toString().trim()
    }

    fun getTag(fullData: ByteArray): Map<String, ByteArray> {
        var tagBytes = ByteArray(0)
        var nextByteCounter = 0
        val firstTagByte = fullData[nextByteCounter++]
        tagBytes += firstTagByte
        if(matchMask(firstTagByte, 0b11111)){
            var newByte = fullData[nextByteCounter++]
            tagBytes += newByte
            while(matchMask(newByte, 0b10000000.toByte())){
                newByte = fullData[nextByteCounter++]
                tagBytes += newByte
            }
        }
        var remaining = ByteArray(0)
        remaining += fullData.takeLast(fullData.size-nextByteCounter)
        return mapOf("TAG" to tagBytes, "REMAINING" to remaining)
    }

    fun getLength(fullData: ByteArray): Map<String, ByteArray>{
        var nextByteCounter = 0
        var lenBytes = ByteArray(0)
        lenBytes += fullData[nextByteCounter++]
        var remaining = ByteArray(0)
        remaining += fullData.takeLast(fullData.size-nextByteCounter)
        return mapOf("LENGTH" to lenBytes, "REMAINING" to remaining)
    }

        private fun matchMask(byte: Byte, mask: Byte): Boolean {
            return (byte and mask == mask)
        }


}