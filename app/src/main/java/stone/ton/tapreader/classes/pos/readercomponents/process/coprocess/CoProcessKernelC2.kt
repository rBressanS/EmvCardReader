package stone.ton.tapreader.classes.pos.readercomponents.process.coprocess

import android.util.Log
import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import stone.ton.tapreader.classes.apdu.APDUCommand
import stone.ton.tapreader.classes.apdu.APDUResponse
import stone.ton.tapreader.classes.dataclasses.kernel.KernelData
import stone.ton.tapreader.classes.emv.AFLEntry
import stone.ton.tapreader.classes.emv.DOL
import stone.ton.tapreader.classes.utils.DataSets
import stone.ton.tapreader.classes.utils.General.Companion.decodeHex
import stone.ton.tapreader.classes.utils.ProcessSelectionResponse

object CoProcessKernelC2 {

    //Capitulo 4 pagina 103 - Data Organization

    lateinit var kernelData:KernelData

    var kernelDatabase:Any = 0

    fun start(fciResponse: APDUResponse){
        val fullPdol = buildPDOLValue(
            fciResponse.getParsedData().find(BerTag(0xA5))
                .find(BerTag(0x9F, 0x38))
        )
        val gpoRequest =
            APDUCommand.getForGetProcessingOptions(fullPdol)
        val gpoResponse = CoProcessPCD.communicateWithCard(gpoRequest)
        val responseTemplate = gpoResponse.getParsedData()
        Log.i("TESTE", responseTemplate.toString())
        val aflValue = responseTemplate.find(BerTag(0x94))
        val aflEntries = aflValue.bytesValue.toList().chunked(4)
        for (data in aflEntries) {
            val aflEntry = AFLEntry(data.toByteArray())
            for (record in aflEntry.start .. aflEntry.end) {
                val sfiP2 = aflEntry.getSfiForP2()
                val readRecord =
                    APDUCommand.getForReadRecord(sfiP2, record.toByte())
                CoProcessPCD.communicateWithCard(readRecord)
            }
        }
    }

    fun buildPDOLValue(tag9F38: BerTlv?): ByteArray {
        val fullPdol: ByteArray
        var pdolValue = ByteArray(0)
        if (tag9F38 != null) {
            val PDOL = DOL(tag9F38.bytesValue)
            for (entry in PDOL.requiredTags) {
                val terminalTag = DataSets.terminalTags.find { it.tag == entry.key }
                if (terminalTag != null && terminalTag.value.decodeHex().size == entry.value) {
                    pdolValue += terminalTag.value.decodeHex()
                } else {
                    pdolValue += MutableList(entry.value) { 0 }
                }
            }
            fullPdol = BerTlvBuilder().addBytes(BerTag(0x83), pdolValue).buildArray()
        } else {
            fullPdol = byteArrayOf(0x83.toByte(), 0x00)
        }
        return fullPdol
    }

}