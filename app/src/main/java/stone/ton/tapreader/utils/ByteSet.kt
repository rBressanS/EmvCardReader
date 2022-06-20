package stone.ton.tapreader.utils

import java.util.*

class ByteSet{

    companion object{

        fun BitSet.setValueForByteAndBit(
            byteNum: Int,
            bitNum: Int,
            value: Boolean,
        ) {
            this.set(getBitNum(byteNum, bitNum), value)
        }

        fun BitSet.setValueForByteRange(
            byteNumStart: Int,
            bitNumStart: Int,
            byteNumEnd:Int,
            bitNumEnd:Int,
            value: BitSet,
        ) {
            val initialPosition = getBitNum(byteNumStart, bitNumStart)
            val finalPosition = getBitNum(byteNumEnd, bitNumEnd)
            for(bitNumber in initialPosition..finalPosition){
                if(bitNumber - initialPosition > value.size()){
                    this.set(bitNumber, false)
                }else{
                    this.set(bitNumber, value.get(bitNumber - initialPosition))
                }
            }
        }

        fun BitSet.flipValueForByteAndBit(byteNum: Int,
                                   bitNum: Int){
            this.flip(getBitNum(byteNum, bitNum))
        }

        fun BitSet.getAsEmvByteArray(): ByteArray{
            var returnable = this.toByteArray()
            while(returnable.size < this.length()/8){
                returnable += byteArrayOf(0x00)
            }
            return returnable
        }

        private fun getBitNum(byteNum:Int, bitNum:Int):Int {
            return byteNum*8 + bitNum
        }
    }

}