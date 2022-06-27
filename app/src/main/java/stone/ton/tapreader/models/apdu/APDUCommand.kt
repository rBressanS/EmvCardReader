package stone.ton.tapreader.models.apdu

import stone.ton.tapreader.pos.process.coprocess.CoProcessKernelC2
import stone.ton.tapreader.utils.General.Companion.decodeHex
import kotlin.experimental.or

class APDUCommand(
    var class_: Byte,
    var instruction: Byte,
    var parameter1: Byte,
    var parameter2: Byte,
    val data: ByteArray? = null,
    private var le: Byte = 0x00
) {


    fun getAsBytes(): ByteArray {
        var bytes = ByteArray(4)
        bytes[0] = class_
        bytes[1] = instruction
        bytes[2] = parameter1
        bytes[3] = parameter2
        if (data != null) {
            if (data.isNotEmpty()) {
                bytes += (data.size).toByte()
                bytes += data
            }
        }

        bytes += byteArrayOf(le)
        return bytes
    }

    companion object {
        fun buildSelectApplication(aid: String): APDUCommand {
            return buildSelectApplication(aid.decodeHex())
        }

        fun buildSelectApplication(aid: ByteArray): APDUCommand {
            return APDUCommand(
                class_ = 0x00,
                instruction = 0xA4.toByte(),
                parameter1 = 0x04,
                parameter2 = 0x00,
                data = aid
            )
        }

        fun buildGetProcessingOptions(pdol: ByteArray): APDUCommand {
            return APDUCommand(
                class_ = 0x80.toByte(),
                instruction = 0xA8.toByte(),
                parameter1 = 0x00,
                parameter2 = 0x00,
                data = pdol
            )
        }

        fun buildReadRecord(sfi: Byte, record: Byte): APDUCommand {
            return APDUCommand(
                class_ = 0x00,
                instruction = 0xB2.toByte(),
                parameter1 = record,
                parameter2 = sfi,
            )
        }

        fun buildExchangeRelayResistanceData(entropyData: ByteArray): APDUCommand {
            return APDUCommand(
                class_ = 0x80.toByte(),
                instruction = 0xEA.toByte(),
                parameter1 = 0x00,
                parameter2 = 0x00,
                data = entropyData
            )
        }

        fun buildGenerateAc(acType:CoProcessKernelC2.ACType, isCda:Boolean, cdolData: ByteArray): APDUCommand {
            var p1:Byte = 0x00
            if(isCda){
                p1 = p1.or(0b10000)
            }
            if(acType == CoProcessKernelC2.ACType.AAC){
                p1 = p1.or(0b0)
            }
            if(acType == CoProcessKernelC2.ACType.TC){
                p1 = p1.or(0b1000000)
            }
            if(acType == CoProcessKernelC2.ACType.ARQC){
                p1 = p1.or(0b10000000.toByte())
            }

            return APDUCommand(
                class_ = 0x80.toByte(),
                instruction = 0xAE.toByte(),
                parameter1 = p1,
                parameter2 = 0x00,
                data = cdolData
            )
        }
    }

}