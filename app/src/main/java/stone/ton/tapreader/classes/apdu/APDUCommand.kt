package stone.ton.tapreader.classes.apdu

class APDUCommand(
    var class_: Byte,
    var instruction: Byte,
    var parameter1: Byte,
    var parameter2: Byte,
    var data: ByteArray?
) {


    fun getAsBytes(): ByteArray{
         var bytes = ByteArray(4)
         bytes[0] = class_
         bytes[1] = instruction
         bytes[2] = parameter1
         bytes[3] = parameter2
        if(data != null){
            if(data!!.isNotEmpty()){
                bytes += (data!!.size - 1).toByte()
                bytes += data!!
            }
        }

        return bytes
    }

}