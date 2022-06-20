package stone.ton.tapreader.pos.process.coprocess

import android.util.Log
import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import stone.ton.tapreader.models.apdu.APDUCommand
import stone.ton.tapreader.models.apdu.APDUResponse
import stone.ton.tapreader.models.emv.AFLEntry
import stone.ton.tapreader.models.emv.DOL
import stone.ton.tapreader.models.kernel.KernelData
import stone.ton.tapreader.models.kernel.TlvDatabase
import stone.ton.tapreader.pos.process.process_d.UserInterfaceRequestData
import stone.ton.tapreader.utils.BerTlvExtension.Companion.getFullAsByteArray
import stone.ton.tapreader.utils.BerTlvExtension.Companion.getListAsByteArray
import stone.ton.tapreader.utils.BerTlvExtension.Companion.getValueAsByteArray
import stone.ton.tapreader.utils.ByteSet.Companion.getAsEmvByteArray
import stone.ton.tapreader.utils.ByteSet.Companion.setValueForByteAndBit
import stone.ton.tapreader.utils.ByteSet.Companion.setValueForByteRange
import stone.ton.tapreader.utils.General.Companion.decodeHex
import stone.ton.tapreader.utils.General.Companion.toHex
import java.util.*

class CoProcessKernelC2 {

    //TODO Tags to read
    //TODO Tags to write

    //TODO Add default values Table 4.3

    //Capitulo 4 pagina 103 - Data Organization

    lateinit var kernelData: KernelData

    var kernelDatabase = TlvDatabase()
    var signedData = ""

    //var outcomeParameter = BitSet(8)
    var outcomeParameter = OutcomeParameter()
    var userInterfaceRequestData = ByteArray(22)
    var errorIndication = ByteArray(6)
    var cvmResult = 0
    var acType = "TC"
    var TVR = 0
    var odaStatus = 0
    var rrpCounter = 0
    var terminalCapabilities = TerminalCapabilities()
    var discretionaryData = DiscretionaryData(ArrayList())

    val discretionaryDataElements = listOf<String>(
        "9F5D",
        "9F42",
        "DF8105",
        "DF8104",
        "DF8102",
        "DF810B",
        "DF8115",
        "DF810E",
        "DF810F",
        "9F6E",
        "FF8101"
    )

    fun syncData(startProcessPayload: StartProcessPayload) {
        for (t in startProcessPayload.syncData) {
            if (t.tag.bytes.component1() == 0x6F.toByte()) {
                kernelDatabase.parseAndStoreCardResponse(startProcessPayload.fciResponse.getParsedData())
            } else {
                kernelDatabase.add(t)
            }
        }
        val languagePreference = kernelDatabase.getTlv(byteArrayOf(0x5F, 0x2D))
        if (languagePreference != null) {
            val lngPref = languagePreference.fullTag.bytesValue
            for((index, lng) in lngPref.withIndex()){
                userInterfaceRequestData[5+index] = lng
            }

        }
    }

    fun communicateWithCard(apduCommand: APDUCommand): APDUResponse {
        val response = CoProcessPCD.communicateWithCard(apduCommand)
        kernelDatabase.parseAndStoreCardResponse(response.getParsedData())
        return response
    }

    fun initializeDiscretionaryData() {
        for(tag in discretionaryDataElements){
            kernelDatabase.runIfExists(tag.decodeHex()) {
                discretionaryData.add(it.fullTag)
            }
        }
    }

    fun buildOutSignal(): OutSignal{
        initializeDiscretionaryData()
        return OutSignal(outcomeParameter, null, DataRecord(),discretionaryData)
    }

    fun start(startProcessPayload: StartProcessPayload): OutSignal? {
        syncData(startProcessPayload)
        if (!kernelDatabase.isPresent("84".decodeHex())) {
            errorIndication[1] = 0x01
            kernelDatabase.add(BerTlvBuilder().addBytes(BerTag("DF8115".decodeHex()),errorIndication).buildTlv())
            outcomeParameter.status = 0x05
            outcomeParameter.start = 0x2
            //TODO improve step 1.8
            return buildOutSignal()
        }

        if (!kernelDatabase.isPresent("9F5D".decodeHex())) {
            //TODO implement support for field off request
        }
        kernelDatabase.runIfExists("DF8117".decodeHex()) {
            terminalCapabilities.byte1 = it.fullTag.intValue
        }

        terminalCapabilities.byte2 = 0

        kernelDatabase.runIfExists("DF811F".decodeHex()) {
            terminalCapabilities.byte3 = it.fullTag.intValue
        }

        //TODO implement algorithm to generate number
        kernelDatabase.add(
            BerTlvBuilder().addHex(BerTag("9F37".decodeHex()), "00000000").buildTlv()
        )

        val fciResponse = startProcessPayload.fciResponse
        val fullPdol = buildDolValue(
            fciResponse.getParsedData().find(BerTag(0xA5))
                .find(BerTag(0x9F, 0x38))
        )
        val gpoRequest =
            APDUCommand.buildGetProcessingOptions(fullPdol)
        val gpoResponse = communicateWithCard(gpoRequest)
        val responseTemplate = gpoResponse.getParsedData()
        Log.i("TESTE", responseTemplate.toString())
        val aflValue = responseTemplate.find(BerTag(0x94))
        val aflEntries = aflValue.bytesValue.toList().chunked(4)
        for (data in aflEntries) {
            val aflEntry = AFLEntry(data.toByteArray())
            for (record in aflEntry.start..aflEntry.end) {
                val sfiP2 = aflEntry.getSfiForP2()
                val readRecord =
                    APDUCommand.buildReadRecord(sfiP2, record.toByte())
                val readRecordResponse = communicateWithCard(readRecord)
                if (aflEntry.isSigned) {
                    signedData += readRecordResponse.getParsedData().getListAsByteArray().toHex()
                }
            }
        }
        if (hasMandatoryCdaTags()) {
            if (kernelDatabase.isPresent(byteArrayOf(0x9F.toByte(), 0x4A))) {
                val sdaTag = kernelDatabase.getTlv(BerTag(0x9F, 0x4A))
                if (sdaTag != null && sdaTag.fullTag.intValue == 0x82) {
                    signedData += sdaTag.fullTag.getFullAsByteArray().toHex()
                }
            }
        }
        return null
    }

    private fun hasMandatoryCdaTags(): Boolean {
        return (kernelDatabase.isPresent(byteArrayOf(0x8F.toByte())) // CA Public Key Index
                && kernelDatabase.isPresent(byteArrayOf(0x90.toByte())) // Issuer Public Key Certificate
                && kernelDatabase.isPresent(byteArrayOf(0x9F32.toByte())) // Issuer Public Key Exponent
                //&& kernelDatabase.isPresent(byteArrayOf(0x92.toByte())) //
                && kernelDatabase.isPresent(byteArrayOf(0x9F46.toByte())) // ICC Public Key Certificate
                && kernelDatabase.isPresent(byteArrayOf(0x9F47.toByte())) // ICC Public Key Exponent
                )
    }

    fun buildDolValue(requestedDol: BerTlv?): ByteArray {
        val fullPdol: ByteArray
        var pdolValue = ByteArray(0)
        if (requestedDol != null) {
            val PDOL = DOL(requestedDol.bytesValue)
            for (entry in PDOL.requiredTags) {
                val terminalTag = kernelDatabase.getTlv(BerTag(entry.key.decodeHex()))
                if (terminalTag != null && terminalTag.fullTag.getValueAsByteArray().size == entry.value) {
                    pdolValue += terminalTag.fullTag.getValueAsByteArray()
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

    class OutcomeParameter {
        var status = 0
        var start = 0
        val onlineResponseData = 0
        val CVM = 0
        val uiRequestOnOutcomePresent = false
        val uiRequestOnRestartPresent = false
        val dataRecordPresent = false
        val didescretionaryDataPresent = false
        val receipt = false
        val alternateInterfacePreference = 0
        val fieldOffRequest = 0
        val removalTimeount = 0

    }

    class DataRecord {

    }

    class DiscretionaryData(var data:MutableList<BerTlv>) {
        fun add(tag: BerTlv){
            data.add(tag)
        }
    }

    class TerminalCapabilities {
        var byte1 = 0
        var byte2 = 0
        var byte3 = 0
    }

    data class OutSignal(
        val outcomeParameter: OutcomeParameter,
        val userInterfaceRequestData: UserInterfaceRequestData?,
        val dataRecord: DataRecord,
        val discretionaryData: DiscretionaryData
    )

    data class StartProcessPayload(val fciResponse: APDUResponse, val syncData: List<BerTlv>)

}