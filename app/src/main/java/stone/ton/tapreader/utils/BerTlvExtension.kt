package stone.ton.tapreader.utils

import com.payneteasy.tlv.BerTlv
import stone.ton.tapreader.utils.BerTlvExtension.Companion.getListAsByteArray
import stone.ton.tapreader.utils.General.Companion.decodeHex

class BerTlvExtension {
    companion object{
        fun BerTlv.getValueAsByteArray(): ByteArray {
            return this.hexValue.decodeHex()
        }

        fun BerTlv.getFullAsByteArray(): ByteArray{
            var returnable = ByteArray(0)

            returnable += this.tag.bytes
            returnable += this.getValueAsByteArray().size.toByte()
            returnable += this.getValueAsByteArray()
            return returnable
        }

        fun BerTlv.getListAsByteArray(): ByteArray{
            var returnable = ByteArray(0)
            for(tag in this.values){
                returnable += tag.getFullAsByteArray()
            }
            return returnable
        }
    }
}