package stone.ton.tapreader.pos.process.coprocess

import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import stone.ton.tapreader.models.apdu.APDUCommand
import stone.ton.tapreader.models.apdu.APDUCommand.Companion.buildExchangeRelayResistanceData
import stone.ton.tapreader.models.apdu.APDUCommand.Companion.buildGenerateAc
import stone.ton.tapreader.models.apdu.APDUResponse
import stone.ton.tapreader.models.emv.AFLEntry
import stone.ton.tapreader.models.emv.DOL
import stone.ton.tapreader.models.kernel.KernelData
import stone.ton.tapreader.models.kernel.TlvDatabase
import stone.ton.tapreader.pos.process.coprocess.CoProcessKernelC2.CvmCode.*
import stone.ton.tapreader.pos.process.process_d.UserInterfaceRequestData
import stone.ton.tapreader.utils.BerTlvExtension.Companion.getFullAsByteArray
import stone.ton.tapreader.utils.BerTlvExtension.Companion.getListAsByteArray
import stone.ton.tapreader.utils.BerTlvExtension.Companion.getValueAsByteArray
import stone.ton.tapreader.utils.BerTlvExtension.Companion.isEmpty
import stone.ton.tapreader.utils.DataSets
import stone.ton.tapreader.utils.General.Companion.and
import stone.ton.tapreader.utils.General.Companion.decodeHex
import stone.ton.tapreader.utils.General.Companion.getIntValue
import stone.ton.tapreader.utils.General.Companion.isBitSet
import stone.ton.tapreader.utils.General.Companion.isBitSetOfByte
import stone.ton.tapreader.utils.General.Companion.or
import stone.ton.tapreader.utils.General.Companion.setBitOfByte
import stone.ton.tapreader.utils.General.Companion.toHex
import java.io.IOException
import kotlin.experimental.and
import kotlin.math.max
import kotlin.math.min

class CoProcessKernelC2 {

    //TODO Tags to read
    //TODO Tags to write

    //TODO Add default values Table 4.3

    //Capitulo 4 pagina 103 - Data Organization

    lateinit var kernelData: KernelData

    private var kernelDatabase = TlvDatabase()
    private var signedData = ""

    //var outcomeParameter = BitSet(8)
    private var outcomeParameter = OutcomeParameter()
    private var userInterfaceRequestData = ByteArray(22)
    private var errorIndication = ByteArray(6)
    private var cvmResult = ByteArray(3)
    var acType = "TC"
    private var odaStatus = ""
    private var rrpCounter = 0
    private var terminalCapabilities = TerminalCapabilities()
    private var discretionaryData = DiscretionaryData(ArrayList())
    private var dataRecord = DataRecord(ArrayList())
    private var tvr = ByteArray(5)
    private var noCdaOptimisation = false

    private val discretionaryDataElements = listOf(
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
        "FF8101",
        "95",
        "9F33",
        "9F34",
        "DF8116",
    )

    private val dataRecordElements = listOf(
        "9F02", // Amount
        "9F03", // Amount, Other
        "9F26", // Application cryptogram
        "5f24", // Application expiration
        "82", // AIP
        "50", // App label
        "5A", // App pan
        "5f34", // App pan SEQ
        "9f12", // App prefered name
        "9f36", // App transaction counter
        "9f07", // AUC
        "9f09", // APP VERSION NUMBER (READER)
        "9f27", // CID
        "9f34", // CVM Results
        "84", // df name
        "9f1e", // idsn
        "9f10", // Issuer application data
        "9f11", // issuer code table index
        "9f24", // PAR
        "9f33", // terminal capabilities
        "9f1a", // terminal country code
        "9f35", // terminal type
        "95", // terminal verification results
        "57", // track 2 equivalent data
        "9f53", // transaction category code
        "5f2a", // transaction currency code
        "9a", // transaction date
        "9c", // transaction type
    )

    private fun syncData(startProcessPayload: StartProcessPayload) {
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
            for ((index, lng) in lngPref.withIndex()) {
                userInterfaceRequestData[5 + index] = lng
            }

        }
    }

    private fun communicateWithCard(apduCommand: APDUCommand): APDUResponse {
        return CoProcessPCD.communicateWithCard(apduCommand)
    }

    private fun initializeDiscretionaryData() {
        for (tag in discretionaryDataElements) {
            kernelDatabase.runIfExists(tag.decodeHex()) {
                discretionaryData.add(it.fullTag)
            }
        }
    }

    private fun initializeDataRecord() {
        for (tag in dataRecordElements) {
            kernelDatabase.runIfExists(tag.decodeHex()) {
                dataRecord.add(it.fullTag)
            }
        }
    }

    private fun buildOutSignal(): OutSignal {
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("DF8115".decodeHex()), errorIndication).buildTlv()
        )
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("95".decodeHex()), tvr).buildTlv()
        )
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("9F33".decodeHex()), terminalCapabilities.toByteArray()).buildTlv()
        )
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("9F34".decodeHex()), cvmResult).buildTlv()
        )
        if(outcomeParameter.dataRecordPresent){
            initializeDataRecord()
        }
        if(outcomeParameter.discretionaryDataPresent){
            initializeDiscretionaryData()
        }
        outcomeParameter.errorIndication = errorIndication
        return OutSignal(outcomeParameter, null, dataRecord, discretionaryData)
    }

    private fun addTagToDatabase(tag: String, value: String) {
        kernelDatabase.add(
            BerTlvBuilder().addHex(BerTag(tag.decodeHex()), value).buildTlv()
        )
    }

    private fun processErrorInGpo(): OutSignal {
        userInterfaceRequestData[0] = 0x1c
        userInterfaceRequestData[1] = 0x00
        outcomeParameter.status = 0x04
        outcomeParameter.uiRequestOnOutcomePresent = true
        errorIndication[5] = 0x1c
        return buildOutSignal()
    }

    private fun getCtlessTrxLimit(aip: BerTlv): Int {
        return if (aip.bytesValue[0].isBitSet(1)) {
            kernelDatabase.getTlv("DF8125".decodeHex())!!.fullTag.intValue
        } else {
            kernelDatabase.getTlv("DF8124".decodeHex())!!.fullTag.intValue
        }
    }

    private fun processFciResponse(fciResponse: APDUResponse): OutSignal? {
        val fullPdol = buildDolValue(
            fciResponse.getParsedData().find(BerTag(0xA5))
                .find(BerTag(0x9F, 0x38))
        )
        val gpoRequest =
            APDUCommand.buildGetProcessingOptions(fullPdol)
        try {
            val gpoResponse = communicateWithCard(gpoRequest)
            if (!gpoResponse.wasSuccessful()) {
                errorIndication[1] = 0x03
                errorIndication[3] = gpoResponse.sw1
                errorIndication[4] = gpoResponse.sw2
                outcomeParameter.fieldOffRequest = 0x00
                outcomeParameter.status = 0x05
                outcomeParameter.start = 0x02
                return buildOutSignal()
            }
            val responseTemplate = gpoResponse.getParsedData()
            if (responseTemplate.tag == BerTag(0x77)) {
                kernelDatabase.parseAndStoreCardResponse(gpoResponse.getParsedData())
            } else {
                if (responseTemplate.tag == BerTag(0x80)) {
                    val aip = responseTemplate.bytesValue.copyOfRange(0, 1)
                    addTagToDatabase("82", aip.toHex())

                    val afl =
                        responseTemplate.bytesValue.copyOfRange(2, responseTemplate.bytesValue.size)
                    addTagToDatabase("94", afl.toHex())
                } else {
                    errorIndication[1] = 0x04
                    return processErrorInGpo()
                }
                if (!kernelDatabase.isPresent("94".decodeHex()) || !kernelDatabase.isPresent("82".decodeHex())) {
                    errorIndication[1] = 0x01
                    return processErrorInGpo()
                }

            }
        } catch (e: IOException) {
            outcomeParameter.status = 0x07
            outcomeParameter.start = 0x01
            errorIndication[0] = 0x02
            return buildOutSignal()
        }
        return null
    }

    private var rrpFailedAfterAttempts = false

    private fun processRRP(entropy: ByteArray): OutSignal? {
        addTagToDatabase("DF8301", entropy.toHex())
        try {
            val exchangeRRD = buildExchangeRelayResistanceData(entropy)
            val rrdResponse = communicateWithCard(exchangeRRD)
            if (!rrdResponse.wasSuccessful()) {
                userInterfaceRequestData[0] = 0b00011100
                userInterfaceRequestData[1] = 0
                outcomeParameter.status = 0x04
                errorIndication[5] = 0b00011100
                errorIndication[1] = 0x03
                errorIndication[3] = rrdResponse.sw1
                errorIndication[4] = rrdResponse.sw2
                return buildOutSignal()
            }
            val rrpResponse = rrdResponse.getParsedData()
            if (rrpResponse.tag == BerTag(0x80) && rrpResponse.bytesValue.size == 10) {
                val deviceRelayResistanceEntropy = rrpResponse.bytesValue.copyOfRange(0, 4)
                val minTimeForProcessingRelayResistanceApdu =
                    rrpResponse.bytesValue.copyOfRange(4, 6)
                val maxTimeForProcessingRelayResistanceApdu =
                    rrpResponse.bytesValue.copyOfRange(6, 8)
                val deviceEstimatedTransmissionTimeForRR = rrpResponse.bytesValue.copyOfRange(8, 10)
                println("Adding RRP tags to database")
                println(deviceRelayResistanceEntropy.toHex())
                println(minTimeForProcessingRelayResistanceApdu.toHex())
                println(maxTimeForProcessingRelayResistanceApdu.toHex())
                println(deviceEstimatedTransmissionTimeForRR.toHex())
                addTagToDatabase("DF8302", deviceRelayResistanceEntropy.toHex())
                addTagToDatabase("DF8303", minTimeForProcessingRelayResistanceApdu.toHex())
                addTagToDatabase("DF8304", maxTimeForProcessingRelayResistanceApdu.toHex())
                addTagToDatabase("DF8305", deviceEstimatedTransmissionTimeForRR.toHex())
                val minTimeForProcessingAsInt =
                    minTimeForProcessingRelayResistanceApdu.getIntValue()
                val maxTimeForProcessingAsInt =
                    maxTimeForProcessingRelayResistanceApdu.getIntValue()
                val timeTakenInNano = rrdResponse.timeTakenInNanoSeconds
                val timeTakenInMicro = timeTakenInNano / 1000
                val timeTakenInHundredsOfMicro = timeTakenInMicro / 100
                println("timeTakenInHundredsOfMicro: $timeTakenInHundredsOfMicro")
                val expectedTransmissionForCommand =
                    kernelDatabase.getTlv("DF8134".decodeHex())!!.fullTag.intValue
                val expectedTransmissionForResponse =
                    kernelDatabase.getTlv("DF8135".decodeHex())!!.fullTag.intValue
                val deviceEstimatedAsInt = deviceEstimatedTransmissionTimeForRR.getIntValue()
                val measuredRelayResistanceProcessingTime = max(
                    0,
                    timeTakenInHundredsOfMicro - expectedTransmissionForCommand -
                            min(
                                deviceEstimatedAsInt,
                                expectedTransmissionForResponse
                            )
                )
                val minRelayResistanceGracePeriod =
                    kernelDatabase.getTlv("DF8132".decodeHex())!!.fullTag.intValue
                val maxRelayResistanceGracePeriod =
                    kernelDatabase.getTlv("DF8133".decodeHex())!!.fullTag.intValue
                tvr.setBitOfByte(0,4,false) // RRP Performed
                tvr.setBitOfByte(1,4,true) // RRP Performed
                if (measuredRelayResistanceProcessingTime < max(
                        0,
                        (minTimeForProcessingAsInt - minRelayResistanceGracePeriod)
                    )
                ) {
                    userInterfaceRequestData[0] = 0b00011100
                    userInterfaceRequestData[1] = 0
                    outcomeParameter.status = 0x04
                    outcomeParameter.uiRequestOnOutcomePresent = true
                    errorIndication[1] = 0x06
                    errorIndication[5] = 0b00100001
                    return buildOutSignal()
                } else {
                    val exceededMaximum =
                        (measuredRelayResistanceProcessingTime > (maxTimeForProcessingAsInt + maxRelayResistanceGracePeriod))
                    //TODO validate time taken
                    if (rrpCounter < 2 && exceededMaximum) {
                        println("RRP RETRYING")
                        println("ProcessingTime:$measuredRelayResistanceProcessingTime")
                        println("Threshold: ${maxTimeForProcessingAsInt + maxRelayResistanceGracePeriod}")
                        rrpCounter++
                        generateUnpredictableNumber()
                        val rrpEntropy =
                            kernelDatabase.getTlv("9F37".decodeHex())!!.fullTag.bytesValue
                        val outSignal = processRRP(rrpEntropy)
                        return if (outSignal != null) {
                            outSignal
                        } else {
                            if (rrpFailedAfterAttempts) {
                                return null
                            }
                            tvr.setBitOfByte(0, 4, false)
                            tvr.setBitOfByte(1, 4, true)
                            println("RRP OK")
                            null
                        }
                    }
                    if (exceededMaximum) {
                        tvr.setBitOfByte(2, 4)
                        println("maximum exceeded")
                    }
                    val rrAccuracyThreshold =
                        kernelDatabase.getTlv("DF8136".decodeHex())!!.fullTag.intValue
                    val rrMismatchThreshold =
                        kernelDatabase.getTlv("DF8137".decodeHex())!!.fullTag.intValue
                    if (deviceEstimatedAsInt != 0 && expectedTransmissionForResponse != 0
                        && !(((deviceEstimatedAsInt * 100 / expectedTransmissionForResponse) < rrMismatchThreshold) ||
                                ((expectedTransmissionForResponse * 100 / deviceEstimatedAsInt) < rrMismatchThreshold) ||
                                (max(
                                    0,
                                    measuredRelayResistanceProcessingTime - minTimeForProcessingAsInt
                                ) > rrAccuracyThreshold)
                                )
                    ) {
                        //32
                        tvr.setBitOfByte(0, 4, false)
                        tvr.setBitOfByte(1, 4, true)
                        println("RRP OK")
                    } else {
                        //31
                        tvr.setBitOfByte(3, 4)
                        println("RRP NOK AFTER ALL TRIES")
                        rrpFailedAfterAttempts = true
                        return null
                    }

                }
            } else {
                userInterfaceRequestData[0] = 0b00011100
                userInterfaceRequestData[1] = 0
                outcomeParameter.status = 0x04
                outcomeParameter.uiRequestOnOutcomePresent = true
                errorIndication[1] = 0x04
                errorIndication[5] = 0b00100001
                return buildOutSignal()
            }
        } catch (e: IOException) {
            userInterfaceRequestData[0] = 0b00100001
            userInterfaceRequestData[1] = 0b00000010
            userInterfaceRequestData[2] = 0
            userInterfaceRequestData[3] = 0
            userInterfaceRequestData[4] = 0
            outcomeParameter.status = 0x04
            outcomeParameter.start = 0x01
            errorIndication[0] = 0x02
            errorIndication[5] = 0b00100001
            return buildOutSignal()
        }
        return null
    }

    private fun generateUnpredictableNumber() {
        //TODO implement algorithm to generate number
        addTagToDatabase("9F37", "76543210")
    }

    fun start(startProcessPayload: StartProcessPayload): OutSignal {
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
            terminalCapabilities.byte1 = it.fullTag.intValue.toByte()
        }

        terminalCapabilities.byte2 = 0

        kernelDatabase.runIfExists("DF811F".decodeHex()) {
            terminalCapabilities.byte3 = it.fullTag.intValue.toByte()
        }


        generateUnpredictableNumber()

        var outSignal = processFciResponse(startProcessPayload.fciResponse)
        if (outSignal != null) {
            return outSignal
        }

        val aip = kernelDatabase.getTlv(byteArrayOf(0x82.toByte()))!!.fullTag

        if (!aip.bytesValue[1].isBitSet(7)) {
            errorIndication[1] = 0x07
            return processErrorInGpo()
        }

        val aflValue = kernelDatabase.getTlv(byteArrayOf(0x94.toByte()))!!.fullTag
        val activeAfl = aflValue.bytesValue
        val readerContactlessTransactionLimit = getCtlessTrxLimit(aip)

        println(aip.hexValue)

        if (aip.bytesValue[1].isBitSet(0)) {
            //Supports RRP
            val rrpEntropy = kernelDatabase.getTlv("9F37".decodeHex())!!.fullTag.bytesValue
            outSignal = processRRP(rrpEntropy)
            if (outSignal != null) {
                return outSignal
            }
        } else {
            //Does not supports RRP
            tvr.setBitOfByte(0, 4)
            tvr.setBitOfByte(1, 4, false)
        }

        if (aip.bytesValue[0].isBitSet(0) && terminalCapabilities.byte3.isBitSet(3)) {
            odaStatus = "CDA"
        } else {
            tvr.setBitOfByte(7, 0) // Offline data authentication was not performed
        }

        var cdol: BerTlv? = null

        val aflEntries = activeAfl.toList().chunked(4)
        for (data in aflEntries) {
            val aflEntry = AFLEntry(data.toByteArray())
            for (record in aflEntry.start..aflEntry.end) {
                val sfiP2 = aflEntry.getSfiForP2()
                val readRecord =
                    APDUCommand.buildReadRecord(sfiP2, record.toByte())
                val readRecordResponse: APDUResponse
                try {
                    readRecordResponse = communicateWithCard(readRecord)
                } catch (e: IOException) {
                    userInterfaceRequestData[0] = 0b00100001
                    userInterfaceRequestData[1] = 0b00000010
                    userInterfaceRequestData[2] = 0
                    userInterfaceRequestData[3] = 0
                    userInterfaceRequestData[4] = 0
                    outcomeParameter.status = 0x04
                    outcomeParameter.start = 0x01
                    errorIndication[0] = 0x02
                    errorIndication[5] = 0b00100001
                    return buildOutSignal()
                }
                if (!readRecordResponse.wasSuccessful()) {
                    userInterfaceRequestData[0] = 0b00011100
                    userInterfaceRequestData[1] = 0
                    outcomeParameter.status = 0x04
                    errorIndication[5] = 0b00011100
                    errorIndication[1] = 0x03
                    errorIndication[3] = readRecordResponse.sw1
                    errorIndication[4] = readRecordResponse.sw2
                    return buildOutSignal()
                }

                val readRecordResponseData = readRecordResponse.getParsedData()
                println("aflEntry.sfi: ${aflEntry.sfi}")
                if (aflEntry.sfi <= 10) {
                    if (!readRecordResponseData.isEmpty() && readRecordResponseData.tag == BerTag(
                            0x70
                        )
                    ) {
                        kernelDatabase.parseAndStoreCardResponse(readRecordResponseData)
                    } else {
                        userInterfaceRequestData[0] = 0b00011100
                        userInterfaceRequestData[1] = 0
                        outcomeParameter.status = 0x04
                        outcomeParameter.uiRequestOnOutcomePresent = true
                        errorIndication[1] = 0x04
                        errorIndication[5] = 0b00100001
                        return buildOutSignal()
                    }
                }
                if (aflEntry.isSigned && odaStatus == "CDA") {
                    if (aflEntry.sfi <= 10) {
                        if (signedData.length < 2048 * 2) {
                            signedData += readRecordResponseData.getListAsByteArray().toHex()
                        } else {
                            tvr.setBitOfByte(2, 0) // CDA Failed
                        }
                    } else {
                        if (readRecordResponseData.getListAsByteArray()
                                .isNotEmpty() && readRecordResponseData.tag == BerTag(0x70) && signedData.length < 2048 * 2
                        ) {
                            signedData += readRecordResponseData.getFullAsByteArray()
                        } else {
                            tvr.setBitOfByte(2, 0) // CDA Failed
                        }
                    }
                } else {
                    if (odaStatus == "CDA" || noCdaOptimisation) {
                        continue
                    } else {
                        if (kernelDatabase.isPresent("5F24".decodeHex()) && //Expiration Date
                            kernelDatabase.isPresent("5A".decodeHex()) && // PAN
                            kernelDatabase.isPresent("5F34".decodeHex()) && // SEQ NUMBER
                            kernelDatabase.isPresent("9F07".decodeHex()) && // APP USAGE CONTROL
                            kernelDatabase.isPresent("8E".decodeHex()) && // CVM LIST
                            kernelDatabase.isPresent("9F0D".decodeHex()) && // IAC DEFAULT
                            kernelDatabase.isPresent("9F0E".decodeHex()) && // IAC DENIAL
                            kernelDatabase.isPresent("9F0F".decodeHex()) && // IAC ONLINE
                            kernelDatabase.isPresent("5F28".decodeHex()) && // ISSUER COUNTRY CODE
                            kernelDatabase.isPresent("57".decodeHex()) && // TRACK2 EQUIVALENT DATA
                            kernelDatabase.isPresent("8C".decodeHex())
                        ) { // CDOL1
                            break
                        }
                    }
                }
            }
        }

        val amountAuthorized = kernelDatabase.getTlv("9F02".decodeHex())?.fullTag?.intValue
            ?: return buildOutSignal() // TODO corrigir page 253 456.13
        if (amountAuthorized > readerContactlessTransactionLimit) {
            return buildOutSignal() // TODO corrigir page 253 456.15
        }
        if (!(kernelDatabase.isPresent("5F24".decodeHex()) && // ISSUER COUNTRY CODE
                    kernelDatabase.isPresent("5A".decodeHex()) && // TRACK2 EQUIVALENT DATA
                    kernelDatabase.isPresent("8C".decodeHex()))
        ) {
            return buildOutSignal() // TODO corrigir page 254 456.17
        }

        if (!hasMandatoryCdaTags()) {
            tvr.setBitOfByte(5, 0) // ICC data missing
            tvr.setBitOfByte(2, 0) // CDA Failed
        }else{
            val caPkIndex = kernelDatabase.getTlv(BerTag(0x8F))!!.fullTag.hexValue!!
            val rid = "A000000004"
            val caPk = DataSets.caPublicKeys.find { it.index == caPkIndex && it.rId == rid }
            if (caPk == null) {
                tvr.setBitOfByte(2, 0) // CDA Failed
            }
            val sdaTag = kernelDatabase.getTlv(BerTag(0x9F, 0x4A))
            if (!(sdaTag != null && sdaTag.fullTag.intValue == 0x82)) {
                return buildOutSignal() // TODO corrigir 257 456.27.1
            }
            if (signedData.length < 2048 * 2) {
                signedData += sdaTag.fullTag.getFullAsByteArray().toHex()
            } else {
                tvr.setBitOfByte(2, 0) // CDA Failed
            }
        }

        val readerCvmRequired = kernelDatabase.getTlv("DF8126".decodeHex())!!.fullTag.intValue
        if (amountAuthorized > readerCvmRequired) {
            outcomeParameter.receipt = true
            terminalCapabilities.byte2 =
                kernelDatabase.getTlv("DF8118".decodeHex())!!.fullTag.intValue.toByte()
        } else {
            terminalCapabilities.byte2 =
                kernelDatabase.getTlv("DF8119".decodeHex())!!.fullTag.intValue.toByte()
        }

        //TODO pre-generate AC balance reading

        processingRestrictions()

        cvmSelection()

        val readerContactlessFloorLimit =
            kernelDatabase.getTlv("DF8123".decodeHex())!!.fullTag.intValue
        if (amountAuthorized > readerContactlessFloorLimit) {
            tvr.setBitOfByte(7, 3) // trx exceeds floor limit
        }

        terminalActionAnalysis()

        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("95".decodeHex()), tvr).buildTlv()
        )
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("9F33".decodeHex()), terminalCapabilities.toByteArray()).buildTlv()
        )
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("9F34".decodeHex()), cvmResult).buildTlv()
        )

        println(acType)

        val genAcCommand:APDUCommand
        cdol = kernelDatabase.getTlv("8C".decodeHex())?.fullTag

        val cdolData = buildDolValue(cdol, false)
        var shouldRequestCda = false
        if(odaStatus=="CDA"){
            if(tvr.isBitSetOfByte(2, 0)){
                // CDA Failed
                val onDeviceCardholderVerificationSupported = aip.bytesValue.isBitSetOfByte(1,0)
                if(onDeviceCardholderVerificationSupported){
                    acType="AAC"
                }
            }else{
                if(acType != "AAC"){
                    shouldRequestCda = true
                }else{
                    val appCapInfo = kernelDatabase.getTlv("9F5D".decodeHex())?.fullTag?.bytesValue
                    val isCdaSupportedOver = appCapInfo != null && appCapInfo.isBitSetOfByte(0,0)
                    if(isCdaSupportedOver){
                        shouldRequestCda = true
                    }
                }
            }
        }
        genAcCommand = buildGenerateAc(acType, shouldRequestCda, cdolData)
        val genAcResponse:APDUResponse
        try {
            genAcResponse = communicateWithCard(genAcCommand)
        } catch (e: IOException) {
            userInterfaceRequestData[0] = 0b00100001
            userInterfaceRequestData[1] = 0b00000010
            userInterfaceRequestData[2] = 0
            userInterfaceRequestData[3] = 0
            userInterfaceRequestData[4] = 0
            outcomeParameter.status = 0x04
            outcomeParameter.start = 0x01
            errorIndication[0] = 0x02
            errorIndication[5] = 0b00100001
            return buildOutSignal()
        }
        if (!genAcResponse.wasSuccessful()) {
            userInterfaceRequestData[0] = 0b00011100
            userInterfaceRequestData[1] = 0
            outcomeParameter.status = 0x04
            errorIndication[5] = 0b00011100
            errorIndication[1] = 0x03
            errorIndication[3] = genAcResponse.sw1
            errorIndication[4] = genAcResponse.sw2
            return buildOutSignal()
        }
        if(!genAcResponse.getParsedData().isEmpty() && genAcResponse.getParsedData().tag == BerTag(
                0x77
            )){
            kernelDatabase.parseAndStoreCardResponse(genAcResponse.getParsedData())
        }else{
            if(!genAcResponse.getParsedData().isEmpty() && genAcResponse.getParsedData().tag == BerTag(
                    0x80
                )){
                val responseMessageDataField = genAcResponse.getParsedData().bytesValue
                val parseNOK = responseMessageDataField.size < 11 ||
                        responseMessageDataField.size > 43 ||
                        kernelDatabase.isPresent("9F27".decodeHex()) ||
                        kernelDatabase.isPresent("9F36".decodeHex()) ||
                        kernelDatabase.isPresent("9F26".decodeHex()) ||
                        (responseMessageDataField.size >11 && kernelDatabase.isPresent("9F10".decodeHex()))
                if(parseNOK){
                    return buildOutSignal()
                }
                kernelDatabase.add(BerTlvBuilder().addBytes(
                    BerTag("9F27".decodeHex()),
                    byteArrayOf(responseMessageDataField[0])
                ).buildTlv())
                kernelDatabase.add(BerTlvBuilder().addBytes(
                    BerTag("9F36".decodeHex()),
                    responseMessageDataField.copyOfRange(1,3)
                ).buildTlv())
                kernelDatabase.add(BerTlvBuilder().addBytes(
                    BerTag("9F26".decodeHex()),
                    responseMessageDataField.copyOfRange(3,11)
                ).buildTlv())
                if(responseMessageDataField.size > 11){
                    kernelDatabase.add(BerTlvBuilder().addBytes(
                        BerTag("9F10".decodeHex()),
                        responseMessageDataField.copyOfRange(11, responseMessageDataField.size)
                    ).buildTlv())
                }
            }
        }

        if(!kernelDatabase.isPresent("9F27".decodeHex()) || !kernelDatabase.isPresent("9F36".decodeHex())){
            return buildOutSignal()
        }
        val cid = kernelDatabase.getTlv("9F27".decodeHex())!!.fullTag
        val shouldContinue = (cid.bytesValue[0].and(0xC0.toByte()) == 0x40.toByte()) ||
                (cid.bytesValue[0].and(0xC0.toByte()) == 0x80.toByte() && (acType == "TC"||acType=="ARQC")) ||
                (cid.bytesValue[0].and(0xC0.toByte()) == 0x00.toByte())
        if(!shouldContinue){
            return buildOutSignal()
        }
        userInterfaceRequestData[0] = 0b11110 //MID = clear display
        userInterfaceRequestData[1] = 0b100 // status = card read successfully
        userInterfaceRequestData[2] = 0b0 // Hold time = 0
        userInterfaceRequestData[3] = 0b0 // Hold time = 0
        userInterfaceRequestData[4] = 0b0 // Hold time = 0

        if(kernelDatabase.isPresent("9F4B".decodeHex())){
            //S910.1
            val caPkIndex = kernelDatabase.getTlv(BerTag(0x8F))!!.fullTag.hexValue!!
            val rid = "A000000004"
            val caPk = DataSets.caPublicKeys.find { it.index == caPkIndex && it.rId == rid }
                ?: return buildOutSignal()
            //Perform CDA
            val ICCPKOk = true
            if(!ICCPKOk){
                tvr.setBitOfByte(2, 0) // CDA Failed
                return buildOutSignal()
            }
            val rrpPerformed = tvr.isBitSetOfByte(1,4) || !tvr.isBitSetOfByte(0,4)
            if(rrpPerformed){
                //S910.4.1
                //TODO Verify SDAD EMV Book2 Section 6.6
                val iccDynamicData = ByteArray(45)
                val iccDynamicNumberLength = iccDynamicData[0]
                if(iccDynamicData.size < (44 + iccDynamicNumberLength)){
                    tvr.setBitOfByte(2, 0) // CDA Failed
                    return buildOutSignal()
                }
                val iccDynamicNumber = iccDynamicData.copyOfRange(1,1+iccDynamicNumberLength)
                val applicationCryptogram = iccDynamicData.copyOfRange(2+iccDynamicNumberLength,10+iccDynamicNumberLength)
                kernelDatabase.add(BerTlvBuilder().addBytes(
                    BerTag("9F4C".decodeHex()),
                    iccDynamicNumber
                ).buildTlv())
                kernelDatabase.add(BerTlvBuilder().addBytes(
                    BerTag("9F26".decodeHex()),
                    applicationCryptogram
                ).buildTlv())

                val terminalRelayResistanceEntropy = kernelDatabase.getTlv("DF8301".decodeHex())!!.fullTag.bytesValue
                val deviceRelayResistanceEntropy = kernelDatabase.getTlv("DF8302".decodeHex())!!.fullTag.bytesValue
                val minTimeForProcessingApdu = kernelDatabase.getTlv("DF8303".decodeHex())!!.fullTag.bytesValue
                val maxTimeForProcessingApdu = kernelDatabase.getTlv("DF8304".decodeHex())!!.fullTag.bytesValue
                val deviceEstimatedTimeForResponse = kernelDatabase.getTlv("DF8305".decodeHex())!!.fullTag.bytesValue

                val cdaTerminalRelayResistanceEntropy = iccDynamicData.copyOfRange(30+iccDynamicNumberLength,30+iccDynamicNumberLength+4)
                val cdaDeviceRelayResistanceEntropy = iccDynamicData.copyOfRange(34+iccDynamicNumberLength,34+iccDynamicNumberLength+4)
                val cdaMinTimeForProcessingApdu = iccDynamicData.copyOfRange(38+iccDynamicNumberLength,38+iccDynamicNumberLength+2)
                val cdaMaxTimeForProcessingApdu = iccDynamicData.copyOfRange(40+iccDynamicNumberLength,40+iccDynamicNumberLength+2)
                val cdaDeviceEstimatedTimeForResponse = iccDynamicData.copyOfRange(42+iccDynamicNumberLength,42+iccDynamicNumberLength+2)
                if(!terminalRelayResistanceEntropy.contentEquals(cdaTerminalRelayResistanceEntropy) ||
                    !deviceRelayResistanceEntropy.contentEquals(cdaDeviceRelayResistanceEntropy) ||
                    !minTimeForProcessingApdu.contentEquals(cdaMinTimeForProcessingApdu) ||
                    !maxTimeForProcessingApdu.contentEquals(cdaMaxTimeForProcessingApdu) ||
                    !deviceEstimatedTimeForResponse.contentEquals(cdaDeviceEstimatedTimeForResponse) ){
                    tvr.setBitOfByte(2, 0) // CDA Failed
                    return buildOutSignal()
                }
            }else{
                //S910.4.0
                //TODO Verify SDAD EMV Book2 Section 6.6
                val iccDynamicData = ByteArray(30)
                val iccDynamicNumberLength = iccDynamicData[0]
                if(iccDynamicData.size < (30 + iccDynamicNumberLength)){
                    tvr.setBitOfByte(2, 0) // CDA Failed
                    return buildOutSignal()
                }
                val iccDynamicNumber = iccDynamicData.copyOfRange(1,1+iccDynamicNumberLength)
                val applicationCryptogram = iccDynamicData.copyOfRange(2+iccDynamicNumberLength,10+iccDynamicNumberLength)
                kernelDatabase.add(BerTlvBuilder().addBytes(
                    BerTag("9F4C".decodeHex()),
                    iccDynamicNumber
                ).buildTlv())
                kernelDatabase.add(BerTlvBuilder().addBytes(
                    BerTag("9F26".decodeHex()),
                    applicationCryptogram
                ).buildTlv())
            }
        }else{
            //S910.30
            val applicationCryptogram = kernelDatabase.getTlv("9F26".decodeHex())?.fullTag
            if(applicationCryptogram == null){
                tvr.setBitOfByte(2, 0) // CDA Failed
                return buildOutSignal()
            }
            if(applicationCryptogram.bytesValue[0].and(0xC0.toByte()) != 0x00.toByte()){
                //34
                if(shouldRequestCda){
                    tvr.setBitOfByte(2, 0) // CDA Failed
                    return buildOutSignal()
                }
                val rrpPerformed = tvr.isBitSetOfByte(1,4) || !tvr.isBitSetOfByte(0,4)
                if(rrpPerformed){
                    //TODO S910.39
                }

            }
        }
        outcomeParameter.dataRecordPresent = true
        val PCII = kernelDatabase.getTlv("DF4B".decodeHex())?.fullTag?.bytesValue
        if(PCII!=null && !PCII.and(byteArrayOf(0x00,0x03,0x0F)).contentEquals(ByteArray(3))){
            //S72
            outcomeParameter.status = 0b100 // END_APPLICATION
            outcomeParameter.start = 0b1 // B
            val phoneMessageTable = kernelDatabase.getTlv("DF8131".decodeHex())!!.fullTag.bytesValue
            for(phoneMessageEntry in phoneMessageTable.toList().chunked(8)){
                val pciiMask = phoneMessageEntry.subList(0,3).toByteArray()
                val pciiValue = phoneMessageEntry.subList(3,6).toByteArray()
                val messageId = phoneMessageEntry.subList(6,7).toByteArray()
                val status = phoneMessageEntry.subList(7,8).toByteArray()
                if(pciiMask.and(PCII).contentEquals(pciiValue)){
                    userInterfaceRequestData[0] = messageId[0]
                    userInterfaceRequestData[1] = status[0]
                    userInterfaceRequestData[2] = 0x0
                    userInterfaceRequestData[3] = 0x0
                    userInterfaceRequestData[4] = 0x0
                    break
                }
            }
        }else{
            //S74
            if(cid.bytesValue[0].and(0xC0.toByte()) == 0x40.toByte()){
                outcomeParameter.status = 0b1 // APPROVED
            }else{
                if(cid.bytesValue[0].and(0xC0.toByte()) == 0x80.toByte()){
                    outcomeParameter.status = 0b11 // Online Request
                }else{
                    outcomeParameter.status = 0b10 // Declined
                }
            }
            userInterfaceRequestData[1] = 0x0 // NOT READY
            if(cid.bytesValue[0].and(0xC0.toByte()) == 0x40.toByte()){
                if(outcomeParameter.CVM == 0b1){
                    userInterfaceRequestData[0] = 0b11010 // APProved_sign
                }else{
                    userInterfaceRequestData[0] = 0b11 // APProved_sign
                }
            }else{
                if(cid.bytesValue[0].and(0xC0.toByte()) == 0x80.toByte()){
                    userInterfaceRequestData[0] = 0b11011 // authorising_wait
                }else{
                    userInterfaceRequestData[0] = 0b111 // authorising_wait
                }
            }
        }
        if(PCII!=null && !PCII.and(byteArrayOf(0x00,0x03,0x0F)).contentEquals(ByteArray(3))){
            outcomeParameter.uiRequestOnRestartPresent = true

        }else{
            outcomeParameter.uiRequestOnOutcomePresent = true
        }
        //Generate ARQC
        return buildOutSignal()
    }

    private fun terminalActionAnalysis(){
        println("terminalActionAnalysis")
        val IACDefault = kernelDatabase.getTlv("9F0D".decodeHex())?.fullTag?.bytesValue ?: "FFFFFFFFFC".decodeHex()
        val IACDenial = kernelDatabase.getTlv("9F0E".decodeHex())?.fullTag?.bytesValue ?: ByteArray(5)
        val IACOnline = kernelDatabase.getTlv("9F0F".decodeHex())?.fullTag?.bytesValue ?: "FFFFFFFFFC".decodeHex()
        val TACDefault = kernelDatabase.getTlv("DF8120".decodeHex())!!.fullTag.bytesValue
        val TACDenial = kernelDatabase.getTlv("DF8121".decodeHex())!!.fullTag.bytesValue
        val TACOnline = kernelDatabase.getTlv("DF8122".decodeHex())!!.fullTag.bytesValue
        val terminalType = kernelDatabase.getTlv("9F35".decodeHex())!!.fullTag.hexValue

        if(!TACDenial.or(IACDenial).and(tvr).contentEquals(ByteArray(5))){
            acType="AAC"
            return
        }
        val terminalIsOnlineOnly = terminalType =="11"||terminalType=="21"||terminalType=="14"||terminalType=="24"||terminalType=="34"
        if(terminalIsOnlineOnly){
            acType= "ARQC"
            return
        }
        val terminalIsOfflineOnly = terminalType =="23"||terminalType=="26"||terminalType=="36"||terminalType=="13"||terminalType=="16"
        if(terminalIsOfflineOnly){
            if(TACDefault.or(IACDefault).and(tvr).contentEquals(ByteArray(5))){
                acType="TC"
                return
            }else{
                acType="AAC"
                return
            }
        }
        if(TACOnline.or(IACOnline).and(tvr).contentEquals(ByteArray(5))){
            acType="TC"
            return
        }else{
            acType="ARQC"
            return
        }
    }

    private fun cvmSelection() {
        println("cvmSelection")
        val aip = kernelDatabase.getTlv(byteArrayOf(0x82.toByte()))!!.fullTag.bytesValue
        val supportsCDCVM = aip.isBitSetOfByte(1, 0)
        val amountAuthorized = kernelDatabase.getTlv("9F02".decodeHex())!!.fullTag.intValue
        val readerCvmRequired = kernelDatabase.getTlv("DF8126".decodeHex())!!.fullTag.intValue
        if (supportsCDCVM) {
            //TODO add kernel configurability
            if (readerCvmRequired > amountAuthorized) {
                cvmResult[0] = 0x01
                cvmResult[1] = 0x00
                cvmResult[2] = 0x02
                outcomeParameter.CVM = 0x00 // No CVM
            } else {
                cvmResult[0] = 0x3F
                cvmResult[1] = 0x00
                cvmResult[2] = 0x02
                outcomeParameter.CVM = 0x03 // Confirmation code verified
            }
            return
        }
        if (!aip.isBitSetOfByte(4, 0)) {
            cvmResult[0] = 0x3F
            cvmResult[1] = 0x00
            cvmResult[2] = 0x00
            outcomeParameter.CVM = 0x00 // No CVM
            return
        }
        val cvmList = kernelDatabase.getTlv("8E".decodeHex())?.fullTag?.bytesValue
        if (!(cvmList != null && cvmList.isNotEmpty())) {
            cvmResult[0] = 0x3F
            cvmResult[1] = 0x00
            cvmResult[2] = 0x00
            outcomeParameter.CVM = 0x00 // No CVM
            tvr.setBitOfByte(5, 0) // ICC Data Missing
            return
        }
        val xAmount = cvmList.copyOfRange(0, 4).getIntValue()
        val yAmount = cvmList.copyOfRange(4, 8).getIntValue()
        val transactionType = kernelDatabase.getTlv("9C".decodeHex())?.fullTag?.hexValue
        var cvRuleOffset = 8
        while (cvRuleOffset < cvmList.size) {
            val cvRule = cvmList.copyOfRange(cvRuleOffset, cvRuleOffset + 2)
            cvRuleOffset += 2
            val cvCondition = CvmCondition.fromText(cvRule[1].toHex())
            val cvCode = cvRule[0]
            val cvRuleShouldContinue = cvCode.isBitSet(6)
            if (cvCondition != null) {

                val shouldApplyCondition = shouldApplyCondition(
                    cvCondition,
                    xAmount,
                    yAmount,
                    amountAuthorized,
                    transactionType
                )
                if (shouldApplyCondition) {
                    val cvmCode = CvmCode.fromInt(cvCode.and(0b00111111).toInt())
                    if (cvmCode == null) {
                        tvr.setBitOfByte(6, 2) // Unrecognized CVM
                        if (cvRuleShouldContinue) {
                            continue
                        }
                        outcomeParameter.CVM = 0x00 // No Cvm
                        tvr.setBitOfByte(7, 2) // Cardholder verification was not successful
                        if (cvmCode == FAIL_CVM) {
                            cvmResult[0] = cvRule[0]
                            cvmResult[1] = cvRule[1]
                            cvmResult[2] = 0x01
                        } else {
                            cvmResult[0] = 0x3F
                            cvmResult[1] = 0x00
                            cvmResult[2] = 0x01
                        }
                        return
                    }
                    //TODO CVM.17
                    val isCvmCodeSupported = terminalCapabilities.supportsCvmCode(cvmCode)
                    if(isCvmCodeSupported && cvmCode.value.and(0x3F) != 0){
                        //CVM.18
                        cvmResult[0] = cvRule[0]
                        cvmResult[1] = cvRule[1]
                        if(cvmCode == ENCIPHERED_ONLINE_PIN){
                            outcomeParameter.CVM=0x02 // Online Pin
                            cvmResult[2] = 0x00
                            tvr.setBitOfByte(2,2) // Online Pin Entered
                            return
                        }
                        if(cvmCode == SIGNATURE){
                            outcomeParameter.CVM=0x01 // Signature
                            outcomeParameter.receipt = true
                            cvmResult[2] = 0x00
                            return
                        }
                        if(cvmCode == NO_CVM){
                            outcomeParameter.CVM=0x00 // NoCvm
                            cvmResult[2] = 0x02
                            return
                        }
                    }else{
                        if(cvRuleShouldContinue){
                            continue
                        }
                        outcomeParameter.CVM = 0x00 // No Cvm
                        tvr.setBitOfByte(7, 2) // Cardholder verification was not successful
                        if (cvmCode == FAIL_CVM) {
                            cvmResult[0] = cvRule[0]
                            cvmResult[1] = cvRule[1]
                            cvmResult[2] = 0x01
                        } else {
                            cvmResult[0] = 0x3F
                            cvmResult[1] = 0x00
                            cvmResult[2] = 0x01
                        }
                        return
                    }
                }

            }
        }
        cvmResult[0] = 0x3F
        cvmResult[1] = 0x00
        cvmResult[2] = 0x01
        outcomeParameter.CVM = 0x00 // No CVM
        tvr.setBitOfByte(7, 2) // Cardholder verification was not successful
        return
    }

    private fun shouldApplyCondition(
        condition: CvmCondition,
        xValue: Int,
        yValue: Int,
        amount: Int,
        transactionType: String?
    ): Boolean {
        //TODO implement should Apply
        return true
    }

    enum class CvmCode(val value: Int) {
        FAIL_CVM(0x00),
        FAIL_CVM_2(0x40),
        PLAINTEXT_OFFLINE_PIN(0b1),
        ENCIPHERED_ONLINE_PIN(0b10),
        PLAINTEXT_OFFLINE_PIN_AND_SIGNATURE(0b11),
        ENCIPHERED_OFFLINE_PIN(0b100),
        ENCIPHERED_OFFLINE_PIN_AND_SIGNATURE(0b101),
        SIGNATURE(0b11110),
        NO_CVM(0b11111);

        companion object {
            fun fromInt(value: Int) = values().firstOrNull { it.value == value }

        }
    }

    enum class CvmCondition(val value: String) {
        ALWAYS("00"),
        IF_UNATTENDED_CASH("01"),
        IF_NOT_UNATTENDED_CASH_NOT_MANUAL_NOT_PURCHASE_WITH_CASHBACK("02"),
        IF_TERMINAL_SUPPORTS("03"),
        IF_MANUAL_CASH("04"),
        IF_PURCHASE_WITH_CASHBACK("05"),
        IF_ON_CURRENCY_UNDER_X("06"),
        IF_ON_CURRENCY_OVER_X("07"),
        IF_ON_CURRENCY_UNDER_Y("08"),
        IF_ON_CURRENCY_OVER_Y("09");

        companion object {
            fun fromText(value: String) = values().firstOrNull { it.value == value }
        }
    }

    private fun validateAppVersionNumber() {
        val cardApplicationVersionNumber =
            kernelDatabase.getTlv("9F08".decodeHex())?.fullTag?.hexValue
        if (cardApplicationVersionNumber != null) {
            val terminalApplicationVersionNumber =
                kernelDatabase.getTlv("9F09".decodeHex())?.fullTag?.hexValue
            if (cardApplicationVersionNumber != terminalApplicationVersionNumber) {
                tvr.setBitOfByte(7, 1) // ICC and terminal have different application versions
            }
        }
    }

    private fun validateExpirationDate() {
        val appEffectiveDate = kernelDatabase.getTlv("5F25".decodeHex())?.fullTag?.hexValue
        val appExpirationDate = kernelDatabase.getTlv("5F24".decodeHex())!!.fullTag.hexValue
        val transactionDate = kernelDatabase.getTlv("9A".decodeHex())!!.fullTag.hexValue
        if (appEffectiveDate != null && appEffectiveDate > transactionDate) {
            tvr.setBitOfByte(5, 1) // App not yet effective
        }
        if (transactionDate > appExpirationDate) {
            tvr.setBitOfByte(6, 1) // App expired
        }
    }

    private fun validateAppUsageControl() {
        val appUsageControl =
            kernelDatabase.getTlv("9F07".decodeHex())?.fullTag?.bytesValue ?: return
        val terminalType = kernelDatabase.getTlv("9F35".decodeHex())!!.fullTag.hexValue
        val additionalTerminalCapabilities =
            kernelDatabase.getTlv("9F40".decodeHex())!!.fullTag.bytesValue

        if ((terminalType == "14" || terminalType == "15" || terminalType == "16") && (additionalTerminalCapabilities.isBitSetOfByte(
                7,
                0
            ))
        ) {
            if (!appUsageControl.isBitSetOfByte(1, 1)) {
                tvr.setBitOfByte(4, 1) // Requested service not allowed for card product
                return
            }
        } else {
            if (!appUsageControl.isBitSetOfByte(0, 0)) {
                tvr.setBitOfByte(4, 1) // Requested service not allowed for card product
                return
            }
        }

        val issuerCountryCode = kernelDatabase.getTlv("5F28".decodeHex())?.fullTag?.hexValue
            ?: return

        val terminalCountryCode = kernelDatabase.getTlv("9F1A".decodeHex())!!.fullTag.hexValue

        val transactionType = kernelDatabase.getTlv("9C".decodeHex())!!.fullTag.hexValue

        val isCashTransaction = transactionType == "01" || transactionType == "17"
        val isSameCountry = terminalCountryCode == issuerCountryCode
        if (isCashTransaction) {

            if (isSameCountry) {
                if (!appUsageControl.isBitSetOfByte(7, 0)) {
                    tvr.setBitOfByte(4, 1) // Requested service not allowed for card product
                }
            } else {
                if (!appUsageControl.isBitSetOfByte(6, 0)) {
                    tvr.setBitOfByte(4, 1) // Requested service not allowed for card product
                }
            }
        }

        val isPurchaseTransaction = transactionType == "00" || transactionType == "09"

        if (isPurchaseTransaction) {
            if (isSameCountry) {
                if (!(appUsageControl.isBitSetOfByte(5, 0) || appUsageControl.isBitSetOfByte(
                        4,
                        0
                    ))
                ) {
                    tvr.setBitOfByte(4, 1) // Requested service not allowed for card product
                }
            } else {
                if (!(appUsageControl.isBitSetOfByte(4, 0) || appUsageControl.isBitSetOfByte(
                        3,
                        0
                    ))
                ) {
                    tvr.setBitOfByte(4, 1) // Requested service not allowed for card product
                }
            }
        }

        val cashbackAmount = kernelDatabase.getTlv("9F03".decodeHex())?.fullTag?.intValue
        if (cashbackAmount != null && cashbackAmount > 0) {
            if (isSameCountry) {
                if (!appUsageControl.isBitSetOfByte(7, 1)) {
                    tvr.setBitOfByte(4, 1) // Requested service not allowed for card product
                }
            } else {
                if (!appUsageControl.isBitSetOfByte(6, 1)) {
                    tvr.setBitOfByte(4, 1) // Requested service not allowed for card product
                }
            }
        }

    }

    private fun processingRestrictions() {
        println("processingRestrictions")
        validateAppVersionNumber()
        validateExpirationDate()
        validateAppUsageControl()
    }

    private fun hasMandatoryCdaTags(): Boolean {
        return (kernelDatabase.isPresent(byteArrayOf(0x8F.toByte())) // CA Public Key Index
                && kernelDatabase.isPresent(byteArrayOf(0x90.toByte())) // Issuer Public Key Certificate
                && kernelDatabase.isPresent("9F32".decodeHex()) // Issuer Public Key Exponent
                && kernelDatabase.isPresent("9F46".decodeHex()) // ICC Public Key Certificate
                && kernelDatabase.isPresent("9F47".decodeHex()) // ICC Public Key Exponent
                && kernelDatabase.isPresent("9F4A".decodeHex()) // Static data authentication tag list
                )
    }

    private fun buildDolValue(requestedDol: BerTlv?, shouldAddTemplate:Boolean = true): ByteArray {
        val fullPdol: ByteArray
        var pdolValue = ByteArray(0)
        if (requestedDol != null) {
            val PDOL = DOL(requestedDol.bytesValue)
            for (entry in PDOL.requiredTags) {
                val terminalTag = kernelDatabase.getTlv(entry.key.decodeHex())
                if (terminalTag != null && terminalTag.fullTag.getValueAsByteArray().size == entry.value) {
                    pdolValue += terminalTag.fullTag.getValueAsByteArray()
                } else {
                    pdolValue += MutableList(entry.value) { 0 }
                }
            }
            if(shouldAddTemplate){
                fullPdol = BerTlvBuilder().addBytes(BerTag(0x83), pdolValue).buildArray()
            }else{
                fullPdol = pdolValue
            }
        } else {
            fullPdol = byteArrayOf(0x83.toByte(), 0x00)
        }
        return fullPdol
    }

    data class OutcomeParameter(
        var status: Int = 0,
        var start: Int = 0,
        val onlineResponseData: Int = 0,
        var CVM: Int = 0,
        var uiRequestOnOutcomePresent: Boolean = false,
        val uiRequestOnRestartPresent: Boolean = false,
        val dataRecordPresent: Boolean = false,
        val discretionaryDataPresent: Boolean = false,
        var receipt: Boolean = false,
        val alternateInterfacePreference: Int = 0,
        var fieldOffRequest: Int = 0,
        val removalTimeout: Int = 0,
        var errorIndication: ByteArray = ByteArray(0)
    )

    class DataRecord(var data: MutableList<BerTlv>) {
        fun add(tag: BerTlv) {
            data.add(tag)
        }
        override fun toString(): String {
            return data.joinToString { it.getFullAsByteArray().toHex() }
        }
    }

    class DiscretionaryData(var data: MutableList<BerTlv>) {
        fun add(tag: BerTlv) {
            data.add(tag)
        }
        override fun toString(): String {
            return data.joinToString { it.getFullAsByteArray().toHex() }
        }
    }

    class TerminalCapabilities {
        var byte1:Byte = 0
        var byte2:Byte = 0
        var byte3:Byte = 0

        fun supportsCvmCode(code: CvmCode):Boolean{
            return when(code){
                FAIL_CVM -> true
                FAIL_CVM_2 -> true
                PLAINTEXT_OFFLINE_PIN -> supportsOfflinePlaintextPin()
                ENCIPHERED_ONLINE_PIN -> supportsOnlineEncipheredPin()
                PLAINTEXT_OFFLINE_PIN_AND_SIGNATURE -> supportsOfflinePlaintextPin() && supportsSignature()
                ENCIPHERED_OFFLINE_PIN -> supportsOfflineEncipheredPin()
                ENCIPHERED_OFFLINE_PIN_AND_SIGNATURE -> supportsOfflineEncipheredPin() && supportsSignature()
                SIGNATURE -> supportsSignature()
                NO_CVM -> supportsNoCvm()
            }
        }

        private fun supportsOfflinePlaintextPin(): Boolean {
            return byte2.isBitSet(7)

        }
        private fun supportsOnlineEncipheredPin():Boolean{
            return byte2.isBitSet(6)
        }
        private fun supportsSignature():Boolean{
            return byte2.isBitSet(5)
        }
        private fun supportsOfflineEncipheredPin():Boolean{
            return byte2.isBitSet(4)
        }
        private fun supportsNoCvm():Boolean{
            return byte2.isBitSet(3)
        }

        fun toByteArray(): ByteArray {
            return byteArrayOf(byte1, byte2, byte3)
        }
    }

    data class OutSignal(
        val outcomeParameter: OutcomeParameter,
        val userInterfaceRequestData: UserInterfaceRequestData?,
        val dataRecord: DataRecord,
        val discretionaryData: DiscretionaryData
    )

    data class StartProcessPayload(val fciResponse: APDUResponse, val syncData: List<BerTlv>)

}
