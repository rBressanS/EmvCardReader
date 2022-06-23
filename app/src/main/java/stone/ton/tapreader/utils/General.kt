package stone.ton.tapreader.utils

import com.payneteasy.tlv.BerTlv
import kotlin.experimental.and
import kotlin.experimental.or

class General {

    companion object {
        private fun isPureAscii(s: ByteArray?): Boolean {
            var result = true
            if (s != null) {
                for (i in s.indices) {
                    val c = s[i].toInt()
                    if (c < 31 || c > 127) {
                        result = false
                        break
                    }
                }
            }
            return result
        }

        fun ByteArray.startsWith(str:String):Boolean{
            val values = str.decodeHex()
            for ((index, va) in values.withIndex()){
                if(va != this[index]){
                    return false
                }
            }
            return true
        }

        fun ByteArray.getIntValue(): Int {
            var i = 0
            var j:Int
            var number = 0
            while (i < this.size) {
                j = this[i].toInt()
                number = number * 256 + if (j < 0) 256.let { j += it; j } else j
                i++
            }
            return number
        }


        fun ByteArray.setBitOfByte(bit:Int, byte:Int, value:Boolean = true){
            if(value){
                this[byte] = this[byte] or ((1 shl bit).toByte())
            }else{
                this[byte] = this[byte] and (1 shl bit).inv().toByte()
            }
        }

        fun Byte.isBitSet(bit:Int):Boolean{
            return this.and((1 shl bit).toByte()) == (1 shl bit).toByte()
        }

        fun ByteArray.isBitSetOfByte(bit:Int, byte:Int):Boolean{
            return this[byte].isBitSet(bit)
        }

        fun Byte.toHex(): String{
            return String.format("%02X" + "", this)
        }

        fun ByteArray.toHex(): String {
            /*!
            This method converts bytes to strings of hex
            */
            val result = StringBuilder()
            for (inputbyte in this) {
                result.append(inputbyte.toHex())
            }
            return result.toString().trim()
        }

        fun String.decodeHex(): ByteArray {
            check((this.replace(" ", "").length % 2) == 0) { "Must have an even length" }

            return replace(" ", "").chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }

        private fun dumpBerTlv(tlv: BerTlv, level: Int = 0) {
            val lvlDump = " ".repeat(level * 4)
            if (tlv.isPrimitive) {
                val isAscii: Boolean = isPureAscii(tlv.bytesValue)
                if (isAscii) {
                    /*readActivity.addToApduTrace(
                        "TLV",
                        lvlDump + tlv.tag.toString() + " - " + tlv.hexValue + " (" + tlv.textValue + ")"
                    )*/
                } else {
                    /*readActivity.addToApduTrace("TLV",
                        lvlDump + tlv.tag.toString() + " - " + tlv.hexValue)*/
                }
            } else {
                // readActivity.addToApduTrace("TLV", lvlDump + tlv.tag.toString())
                for (tag in tlv.values) {
                    dumpBerTlv(tag, level + 1)
                }
            }
        }
    }


}