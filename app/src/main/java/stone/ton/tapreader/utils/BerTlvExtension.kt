package stone.ton.tapreader.utils

import com.payneteasy.tlv.BerTlv
import stone.ton.tapreader.utils.General.Companion.decodeHex

class BerTlvExtension {
    companion object {
        fun BerTlv.getValueAsByteArray(): ByteArray {
            return this.hexValue.decodeHex()
        }

        fun BerTlv.getFullAsByteArray(): ByteArray {
            var returnable = ByteArray(0)

            returnable += this.tag.bytes
            returnable += this.getBerLen()
            returnable += if (isPrimitive) {
                this.getValueAsByteArray()
            } else {
                this.getListAsByteArray()
            }

            return returnable
        }

        fun BerTlv.getListAsByteArray(): ByteArray {
            var returnable = ByteArray(0)
            for (tag in this.values) {
                returnable += tag.getFullAsByteArray()
            }
            return returnable
        }

        private fun BerTlv.getBerLen(): ByteArray {
            val aLength: Int = if (isPrimitive) {
                this.bytesValue.size
            } else {
                this.getListAsByteArray().size
            }

            val berLen: ByteArray
            berLen = if (aLength < 0x80) {
                byteArrayOf(aLength.toByte())
            } else if (aLength < 0x100) {
                byteArrayOf(0x81.toByte(), aLength.toByte())
            } else if (aLength < 0x10000) {
                byteArrayOf(0x82.toByte(), (aLength / 0x100).toByte(), (aLength % 0x100).toByte())
            } else if (aLength < 0x1000000) {
                byteArrayOf(
                    0x83.toByte(),
                    (aLength / 0x10000).toByte(),
                    (aLength / 0x100).toByte(),
                    (aLength % 0x100).toByte()
                )
            } else {
                throw IllegalStateException("length [$aLength] out of range (0x1000000)")
            }
            return berLen
        }
    }
}