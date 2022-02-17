package stone.ton.tapreader.classes.apdu

class APDUCommand(
    var class_: Byte,
    var instruction: Byte,
    var parameter1: Byte,
    var parameter2: Byte,
    var data: ByteArray? = null,
    var le: Byte = 0x00
) {



    fun getAsBytes(): ByteArray{
         var bytes = ByteArray(4)
         bytes[0] = class_
         bytes[1] = instruction
         bytes[2] = parameter1
         bytes[3] = parameter2
        if(data != null){
            if(data!!.isNotEmpty()){
                bytes += (data!!.size).toByte()
                bytes += data!!
            }
        }

        bytes += byteArrayOf(le)
        return bytes
    }

    companion object {
        fun getForSelectApplication(aid: ByteArray): APDUCommand{
            return APDUCommand(
                class_ = 0x00,
                instruction = 0xA4.toByte(),
                parameter1 = 0x04,
                parameter2 = 0x00,
                data = aid
            )
        }

        fun getForReadRecord(sfi: Byte, record: Byte): APDUCommand{
            return APDUCommand(
                class_ = 0x00,
                instruction = 0xB2.toByte(),
                parameter1 = record,
                parameter2 = sfi,
            )
        }
    }

}