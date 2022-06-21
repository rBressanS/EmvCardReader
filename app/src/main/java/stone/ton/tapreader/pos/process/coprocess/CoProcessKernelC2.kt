package stone.ton.tapreader.pos.process.coprocess

import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import stone.ton.tapreader.models.apdu.APDUCommand
import stone.ton.tapreader.models.apdu.APDUCommand.Companion.buildExchangeRelayResistanceData
import stone.ton.tapreader.models.apdu.APDUResponse
import stone.ton.tapreader.models.emv.AFLEntry
import stone.ton.tapreader.models.emv.DOL
import stone.ton.tapreader.models.kernel.KernelData
import stone.ton.tapreader.models.kernel.TlvDatabase
import stone.ton.tapreader.pos.process.process_d.UserInterfaceRequestData
import stone.ton.tapreader.utils.BerTlvExtension.Companion.getFullAsByteArray
import stone.ton.tapreader.utils.BerTlvExtension.Companion.getListAsByteArray
import stone.ton.tapreader.utils.BerTlvExtension.Companion.getValueAsByteArray
import stone.ton.tapreader.utils.General.Companion.decodeHex
import stone.ton.tapreader.utils.General.Companion.setBitOfByte
import stone.ton.tapreader.utils.General.Companion.toHex
import java.io.IOException
import kotlin.experimental.and

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
    var tvr = ByteArray(5)

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
        return response
    }

    fun initializeDiscretionaryData() {
        for(tag in discretionaryDataElements){
            kernelDatabase.runIfExists(tag.decodeHex()) {
                discretionaryData.add(it.fullTag)
            }
        }
    }

    private fun buildOutSignal(): OutSignal{
        kernelDatabase.add(BerTlvBuilder().addBytes(BerTag("DF8115".decodeHex()),errorIndication).buildTlv())
        initializeDiscretionaryData()
        return OutSignal(outcomeParameter, null, DataRecord(),discretionaryData)
    }

    private fun addTagToDatabase(tag:String, value:String){
        kernelDatabase.add(
            BerTlvBuilder().addHex(BerTag(tag.decodeHex()), value).buildTlv()
        )
    }

    private fun isBitSet(n: Byte, k: Byte) :Boolean{
        return (n and ((1 shl k - 1).toByte())) != 0.toByte()
    }

    private fun processErrorInGpo():OutSignal{
        userInterfaceRequestData[0] = 0x1c
        userInterfaceRequestData[1] = 0x00
        outcomeParameter.status = 0x04
        outcomeParameter.uiRequestOnOutcomePresent = true
        errorIndication[5] = 0x1c
        return buildOutSignal()
    }

    private fun getCtlessTrxLimit(aip:BerTlv): Int{
        return if(isBitSet(aip.bytesValue[0], 2)){
            kernelDatabase.getTlv("DF8125".decodeHex())!!.fullTag.intValue
        }else{
            kernelDatabase.getTlv("DF8124".decodeHex())!!.fullTag.intValue
        }
    }

    private fun processFciResponse(fciResponse: APDUResponse): OutSignal?{
        val fullPdol = buildDolValue(
            fciResponse.getParsedData().find(BerTag(0xA5))
                .find(BerTag(0x9F, 0x38))
        )
        val gpoRequest =
            APDUCommand.buildGetProcessingOptions(fullPdol)
        try {
            val gpoResponse = communicateWithCard(gpoRequest)
            if(!gpoResponse.wasSuccessful()){
                errorIndication[1] = 0x03
                errorIndication[3] = gpoResponse.sw1
                errorIndication[4] = gpoResponse.sw2
                outcomeParameter.fieldOffRequest = 0x00
                outcomeParameter.status = 0x05
                outcomeParameter.start = 0x02
                return buildOutSignal()
            }
            val responseTemplate = gpoResponse.getParsedData()
            if(responseTemplate.tag == BerTag(0x77)){
                kernelDatabase.parseAndStoreCardResponse(gpoResponse.getParsedData())
            }else{
                if(responseTemplate.tag == BerTag(0x80)){
                    val aip = responseTemplate.bytesValue.copyOfRange(0,1)
                    addTagToDatabase("82",aip.toHex())

                    val afl = responseTemplate.bytesValue.copyOfRange(2, responseTemplate.bytesValue.size)
                    addTagToDatabase("94",afl.toHex())
                }else{
                    errorIndication[1] = 0x04
                    return processErrorInGpo()
                }
                if(!kernelDatabase.isPresent("94".decodeHex()) || !kernelDatabase.isPresent("82".decodeHex())){
                    errorIndication[1] = 0x01
                    return processErrorInGpo()
                }

            }
        }catch (e: IOException){
            outcomeParameter.status = 0x07
            outcomeParameter.start = 0x01
            errorIndication[0]=0x02
            return buildOutSignal()
        }
        return null
    }

    private fun processRRP(entropy:ByteArray): OutSignal?{
        try {
            val exchangeRRD = buildExchangeRelayResistanceData(entropy)
            val begin = System.nanoTime()
            val rrdResponse = communicateWithCard(exchangeRRD)
            val end = System.nanoTime()
            if(!rrdResponse.wasSuccessful()){
                userInterfaceRequestData[0] = 0b00011100
                userInterfaceRequestData[1] = 0
                outcomeParameter.status = 0x04
                errorIndication[5] = 0b00011100
                errorIndication[1] = 0x03
                errorIndication[3] = rrdResponse.sw1
                errorIndication[4] = rrdResponse.sw2
                return buildOutSignal()
            }
        }catch (e: IOException){
            userInterfaceRequestData[0] = 0b00100001
            userInterfaceRequestData[1] = 0b00000010
            userInterfaceRequestData[2] = 0
            userInterfaceRequestData[3] = 0
            userInterfaceRequestData[4] = 0
            outcomeParameter.status = 0x04
            outcomeParameter.start = 0x01
            errorIndication[0]=0x02
            errorIndication[5]= 0b00100001
            return buildOutSignal()
        }
        return null
    }

    fun start(startProcessPayload: StartProcessPayload): OutSignal? {
        syncData(startProcessPayload)
        if (!kernelDatabase.isPresent("84".decodeHex())) {
            errorIndication[1] = 0x01
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
        addTagToDatabase("9F37","00000000")

        var outSignal = processFciResponse(startProcessPayload.fciResponse)
        if(outSignal != null){
            return outSignal
        }

        val aip = kernelDatabase.getTlv(byteArrayOf(0x82.toByte()))!!.fullTag

        if(!isBitSet(aip.bytesValue[1],8)){
            errorIndication[1] = 0x07
            return processErrorInGpo()
        }

        val aflValue = kernelDatabase.getTlv(byteArrayOf(0x94.toByte()))!!.fullTag
        val activeAfl = aflValue.bytesValue
        var readerContactlessTransactionLimit = getCtlessTrxLimit(aip)

        println(aip.hexValue)

        if (isBitSet(aip.bytesValue[1],1) ){
            //Supports RRP
            val rrpEntropy = kernelDatabase.getTlv("9F37".decodeHex())!!.fullTag.bytesValue
            outSignal = processRRP(rrpEntropy)
            if(outSignal != null){
                return outSignal
            }
        }else{
            //Does not supports RRP
            tvr.setBitOfByte(1, 4)
            tvr.setBitOfByte(2, 4, false)
        }

        val aflEntries = activeAfl.toList().chunked(4)
        for (data in aflEntries) {
            val aflEntry = AFLEntry(data.toByteArray())
            for (record in aflEntry.start..aflEntry.end) {
                val sfiP2 = aflEntry.getSfiForP2()
                val readRecord =
                    APDUCommand.buildReadRecord(sfiP2, record.toByte())
                val readRecordResponse = communicateWithCard(readRecord)
                kernelDatabase.parseAndStoreCardResponse(readRecordResponse.getParsedData())
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
        var uiRequestOnOutcomePresent = false
        val uiRequestOnRestartPresent = false
        val dataRecordPresent = false
        val didescretionaryDataPresent = false
        val receipt = false
        val alternateInterfacePreference = 0
        var fieldOffRequest = 0
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