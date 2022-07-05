package stone.ton.tapreader.pos.process.coprocess

import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import stone.ton.tapreader.interfaces.IByteable
import stone.ton.tapreader.models.apdu.APDUCommand
import stone.ton.tapreader.models.apdu.APDUCommand.Companion.buildExchangeRelayResistanceData
import stone.ton.tapreader.models.apdu.APDUCommand.Companion.buildGenerateAc
import stone.ton.tapreader.models.apdu.APDUResponse
import stone.ton.tapreader.models.emv.AFLEntry
import stone.ton.tapreader.models.emv.DOL
import stone.ton.tapreader.models.emv.MessageIdentifier
import stone.ton.tapreader.models.kernel.KernelData
import stone.ton.tapreader.models.kernel.TlvDatabase
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
import stone.ton.tapreader.utils.General.Companion.toHex
import java.io.IOException
import kotlin.experimental.and
import kotlin.experimental.or
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

    private var outcomeParameter = OutcomeParameter()
    private var userInterfaceRequestData = UserInterfaceRequestData()
    private var errorIndication = ErrorIndication()
    private var cvmResult = CvmResults()
    private var acType: ACType = ACType.TC
    private var odaStatus = ""
    private var rrpCounter = 0
    private var terminalCapabilities = TerminalCapabilities()
    private var discretionaryData = DiscretionaryData()
    private var dataRecord = DataRecord()
    private var tvr = TerminalVerificationResult()//ByteArray(5)
    private var noCdaOptimisation = false

    private val defaultTlvValues = mapOf(
        "9f40" to "0000000000".decodeHex(),
        "9f09" to "0002".decodeHex(),
        "df8117" to "00".decodeHex(),
        "df8118" to "00".decodeHex(),
        "df8119" to "00".decodeHex(),
        "df811a" to "9F6A04".decodeHex(),
        "df8130" to "0D".decodeHex(),
        "df811b" to "00".decodeHex(),
        "df810c" to "02".decodeHex(),
        "9f6d" to "0001".decodeHex(),
        "df811e" to "F0".decodeHex(),
        "df812c" to "F0".decodeHex(),
        "df811c" to "012c".decodeHex(),
        "df811d" to "00".decodeHex(),
        "df812d" to "000013".decodeHex(),
        "df8133" to "0032".decodeHex(),
        "df8132" to "0014".decodeHex(),
        "df8131" to "000001000001200000080000080020000004000004002000000100000100200000020000020020000000000000000700".decodeHex(),
        "df8123" to "000000000000".decodeHex(),
        "df8124" to "000000000000".decodeHex(),
        "df8125" to "000000000000".decodeHex(),
        "df8126" to "000000000000".decodeHex(),
        "df8136" to "012c".decodeHex(),
        "df8137" to "32".decodeHex(),
        "df811f" to "00".decodeHex(),
        "df8120" to "840000000C".decodeHex(),
        "df8121" to "840000000C".decodeHex(),
        "df8122" to "840000000C".decodeHex(),
        "9f1a" to "0000".decodeHex(),
        "df8134" to "0012".decodeHex(),
        "df8135" to "0018".decodeHex(),
        "9f35" to "00".decodeHex(),
        "df8127" to "01f4".decodeHex(),
        "9c" to "00".decodeHex(),
    )

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
    ).sorted()

    private fun syncData(startProcessPayload: StartProcessPayload) {
        for(tag in defaultTlvValues){
            kernelDatabase.add(
                BerTlvBuilder().addBytes(BerTag(tag.key.decodeHex()), tag.value).buildTlv()
            )
        }
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
            for (lng in lngPref) {
                val currLng = UserInterfaceRequestData.LanguagePreference.from(lngPref.toHex())
                if (currLng != null) {
                    userInterfaceRequestData.languagePreference = currLng
                    break
                }
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
            BerTlvBuilder().addBytes(BerTag("DF8115".decodeHex()), errorIndication.toByteArray())
                .buildTlv()
        )
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("95".decodeHex()), tvr.toByteArray()).buildTlv()
        )
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("9F33".decodeHex()), terminalCapabilities.toByteArray())
                .buildTlv()
        )
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("9F34".decodeHex()), cvmResult.toByteArray()).buildTlv()
        )
        if (outcomeParameter.dataRecordPresent) {
            initializeDataRecord()
        }
        if (outcomeParameter.discretionaryDataPresent) {
            initializeDiscretionaryData()
        }
        return OutSignal(outcomeParameter, null, dataRecord, discretionaryData)
    }

    private fun addTagToDatabase(tag: String, value: String) {
        kernelDatabase.add(
            BerTlvBuilder().addHex(BerTag(tag.decodeHex()), value).buildTlv()
        )
    }

    private fun buildOutSignalForParsingError() :OutSignal?{
        return null
    }

    private fun buildOutSignalForCardDataMissing():OutSignal?{
        return null
    }

    private fun buildOutSignalForTransmissionError():OutSignal{
        outcomeParameter.status = OutcomeParameter.Status.END_APPLICATION
        outcomeParameter.start = OutcomeParameter.Start.B
        outcomeParameter.uiRequestOnOutcomePresent = true
        errorIndication.l1 = ErrorIndication.L1.TRANSMISSION_ERROR
        errorIndication.msgOnError = MessageIdentifier.TRY_AGAIN
        return buildOutSignal()
    }

    private fun processErrorInGpo(): OutSignal {
        outcomeParameter.status = OutcomeParameter.Status.SELECT_NEXT
        outcomeParameter.start = OutcomeParameter.Start.C
        return buildOutSignal()
    }

    private fun getCtlessTrxLimit(aip: BerTlv): Int {
        return if (aip.bytesValue[0].isBitSet(1)) {
            kernelDatabase.getTlv("DF8125")!!.fullTag.intValue
        } else {
            kernelDatabase.getTlv("DF8124")!!.fullTag.intValue
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
                errorIndication.l2 = ErrorIndication.L2.STATUS_BYTES
                errorIndication.sw1 = gpoResponse.sw1
                errorIndication.sw2 = gpoResponse.sw2
                outcomeParameter.fieldOffRequest = 0x00
                outcomeParameter.status = OutcomeParameter.Status.SELECT_NEXT
                outcomeParameter.start = OutcomeParameter.Start.C
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
                    errorIndication.l2 = ErrorIndication.L2.PARSING_ERROR
                    return processErrorInGpo()
                }
                if (!kernelDatabase.isPresent("94".decodeHex()) || !kernelDatabase.isPresent("82".decodeHex())) {
                    errorIndication.l2 = ErrorIndication.L2.CARD_DATA_MISSING

                    return processErrorInGpo()
                }

            }
        } catch (e: IOException) {
            return buildOutSignalForTransmissionError()
        }
        return null
    }

    private var rrpFailedAfterAttempts = false

    private fun isRrpResponseOk(rrpResponse: BerTlv): Boolean {
        return rrpResponse.tag == BerTag(0x80) && rrpResponse.bytesValue.size == 10
    }

    private fun getRrpProcessingTime(
        timeTakenInHundredsOfMicro: Long,
        expectedTransmissionForCommand: Int,
        deviceEstimatedAsInt: Int,
        expectedTransmissionForResponse: Int
    ): Long {
        return max(
            0,
            timeTakenInHundredsOfMicro - expectedTransmissionForCommand -
                    min(
                        deviceEstimatedAsInt,
                        expectedTransmissionForResponse
                    )
        )
    }

    private fun setErrorIndicationForSw(response: APDUResponse) {
        errorIndication.l2 = ErrorIndication.L2.STATUS_BYTES
        errorIndication.sw1 = response.sw1
        errorIndication.sw2 = response.sw2
    }

    private fun parseRrpResponse(rrpResponse: BerTlv): Map<String, Int> {
        val deviceRelayResistanceEntropy = rrpResponse.bytesValue.copyOfRange(0, 4)
        val minTimeForProcessingRelayResistanceApdu =
            rrpResponse.bytesValue.copyOfRange(4, 6)
        val maxTimeForProcessingRelayResistanceApdu =
            rrpResponse.bytesValue.copyOfRange(6, 8)
        val deviceEstimatedTransmissionTimeForRR = rrpResponse.bytesValue.copyOfRange(8, 10)

        val returnable = HashMap<String, Int>()
        addTagToDatabase("DF8302", deviceRelayResistanceEntropy.toHex())
        returnable["DF8302"] = deviceRelayResistanceEntropy.getIntValue()

        addTagToDatabase("DF8303", minTimeForProcessingRelayResistanceApdu.toHex())
        returnable["DF8303"] = minTimeForProcessingRelayResistanceApdu.getIntValue()

        addTagToDatabase("DF8304", maxTimeForProcessingRelayResistanceApdu.toHex())
        returnable["DF8304"] = maxTimeForProcessingRelayResistanceApdu.getIntValue()

        addTagToDatabase("DF8305", deviceEstimatedTransmissionTimeForRR.toHex())
        returnable["DF8305"] = deviceEstimatedTransmissionTimeForRR.getIntValue()
        return returnable
    }

    private fun validateRrpMismatchAndThreshold(
        deviceEstimatedTimeForProcessing: Int,
        terminalExpectedTransmissionTimeForResponse: Int,
        rrTimeMismatchThreshold: Int,
        measuredProcessingTime: Long,
        deviceMinTimeForProcessing: Int,
        rrAccuracyThreshold: Int
    ): Boolean {
        return !(((deviceEstimatedTimeForProcessing * 100 / terminalExpectedTransmissionTimeForResponse) < rrTimeMismatchThreshold) ||
                ((terminalExpectedTransmissionTimeForResponse * 100 / deviceEstimatedTimeForProcessing) < rrTimeMismatchThreshold) ||
                (
                        max(
                            0,
                            measuredProcessingTime - deviceMinTimeForProcessing
                        ) > rrAccuracyThreshold
                        ))
    }

    fun validateRrdResponse(rrdResponse: APDUResponse): OutSignal? {
        if (!rrdResponse.wasSuccessful()) {
            userInterfaceRequestData.messageIdentifier = MessageIdentifier.TRY_ANOTHER_CARD
            userInterfaceRequestData.status = UserInterfaceRequestData.Status.NOT_READY
            outcomeParameter.status = OutcomeParameter.Status.END_APPLICATION
            outcomeParameter.uiRequestOnOutcomePresent = true
            errorIndication.msgOnError = MessageIdentifier.TRY_ANOTHER_CARD
            setErrorIndicationForSw(rrdResponse)
            return buildOutSignal()
        }
        val rrpResponse = rrdResponse.getParsedData()
        if (!isRrpResponseOk(rrpResponse)) {
            userInterfaceRequestData.messageIdentifier = MessageIdentifier.TRY_ANOTHER_CARD
            userInterfaceRequestData.status = UserInterfaceRequestData.Status.NOT_READY
            outcomeParameter.status = OutcomeParameter.Status.END_APPLICATION
            outcomeParameter.uiRequestOnOutcomePresent = true
            errorIndication.l2 = ErrorIndication.L2.PARSING_ERROR
            errorIndication.msgOnError = MessageIdentifier.TRY_AGAIN
            return buildOutSignal()
        }
        return null
    }

    private fun processRRP(entropy: ByteArray): OutSignal? {
        try {
            val exchangeRRD = buildExchangeRelayResistanceData(entropy)
            val rrdResponse = communicateWithCard(exchangeRRD)
            var outSignal = validateRrdResponse(rrdResponse)
            if (outSignal != null) {
                return outSignal
            }
            val rrpResponse = rrdResponse.getParsedData()

            val deviceTimes = parseRrpResponse(rrpResponse)

            val deviceMinTimeForProcessing = deviceTimes["DF8303"]!!
            val deviceMaxTimeForProcessing = deviceTimes["DF8304"]!!
            val deviceEstimatedTimeForProcessing = deviceTimes["DF8305"]!!

            val timeTakenInNano = rrdResponse.timeTakenInNanoSeconds

            val measuredReaderTime = timeTakenInNano / 100000

            println("timeTakenInHundredsOfMicro: $measuredReaderTime")
            val terminalExpectedTransmissionTimeForCommand =
                kernelDatabase.getTlv("DF8134")!!.fullTag.intValue

            val terminalExpectedTransmissionTimeForResponse =
                kernelDatabase.getTlv("DF8135")!!.fullTag.intValue

            val measuredProcessingTime = getRrpProcessingTime(
                measuredReaderTime,
                terminalExpectedTransmissionTimeForCommand,
                deviceEstimatedTimeForProcessing,
                terminalExpectedTransmissionTimeForResponse
            )

            val terminalMinGracePeriod = kernelDatabase.getTlv("DF8132")!!.fullTag.intValue
            val terminalMaxGracePeriod = kernelDatabase.getTlv("DF8133")!!.fullTag.intValue
            tvr.relayResistancePerformed =
                TerminalVerificationResult.RelayResistancePerformed.RRP_PERFORMED
            val minProcessingTime = max(
                0,
                (deviceMinTimeForProcessing - terminalMinGracePeriod)
            )
            if (measuredProcessingTime < minProcessingTime) {
                userInterfaceRequestData.messageIdentifier = MessageIdentifier.TRY_ANOTHER_CARD
                userInterfaceRequestData.status = UserInterfaceRequestData.Status.NOT_READY
                outcomeParameter.status = OutcomeParameter.Status.END_APPLICATION
                outcomeParameter.uiRequestOnOutcomePresent = true
                errorIndication.l2 = ErrorIndication.L2.CARD_DATA_ERROR
                errorIndication.msgOnError = MessageIdentifier.TRY_AGAIN
                return buildOutSignal()
            }
            val maxProcessingTime = max(0, deviceMaxTimeForProcessing + terminalMaxGracePeriod)

            val exceededMaximum = measuredProcessingTime > maxProcessingTime

            if (rrpCounter < 2 && exceededMaximum) {
                println("RRP RETRYING")
                println("ProcessingTime:$measuredProcessingTime")
                println("Threshold: ${deviceMaxTimeForProcessing + terminalMaxGracePeriod}")
                rrpCounter++
                generateUnpredictableNumber()
                val rrpEntropy = kernelDatabase.getTlv("9F37")!!.fullTag.bytesValue
                outSignal = processRRP(rrpEntropy)
                return if (outSignal != null) {
                    outSignal
                } else {
                    if (rrpFailedAfterAttempts) {
                        return null
                    }
                    tvr.relayResistancePerformed =
                        TerminalVerificationResult.RelayResistancePerformed.RRP_PERFORMED
                    println("RRP OK")
                    null
                }
            }
            if (exceededMaximum) {
                tvr.relayResistanceTimeLimitsExceeded = true
            }
            val rrAccuracyThreshold = kernelDatabase.getTlv("DF8136")!!.fullTag.intValue
            val rrTimeMismatchThreshold = kernelDatabase.getTlv("DF8137")!!.fullTag.intValue
            val shouldCheckMismatchAndThreshold = (deviceEstimatedTimeForProcessing != 0 &&
                    terminalExpectedTransmissionTimeForResponse != 0)
            if (shouldCheckMismatchAndThreshold &&
                validateRrpMismatchAndThreshold(
                    deviceEstimatedTimeForProcessing,
                    terminalExpectedTransmissionTimeForResponse,
                    rrTimeMismatchThreshold,
                    measuredProcessingTime,
                    deviceMinTimeForProcessing,
                    rrAccuracyThreshold
                )
            ) {
                //32
                tvr.relayResistancePerformed =
                    TerminalVerificationResult.RelayResistancePerformed.RRP_PERFORMED
                println("RRP OK")
                return null
            }
            tvr.relayResistanceTimeLimitsExceeded = true
            println("RRP NOK AFTER ALL TRIES")
            rrpFailedAfterAttempts = true
            return null
        } catch (e: IOException) {
            userInterfaceRequestData.messageIdentifier = MessageIdentifier.TRY_AGAIN
            userInterfaceRequestData.status = UserInterfaceRequestData.Status.READY_TO_READ
            userInterfaceRequestData.holdTime = 0
            outcomeParameter.status = OutcomeParameter.Status.END_APPLICATION
            outcomeParameter.start = OutcomeParameter.Start.B
            outcomeParameter.uiRequestOnOutcomePresent = true
            errorIndication.l1 = ErrorIndication.L1.TRANSMISSION_ERROR
            errorIndication.msgOnError = MessageIdentifier.TRY_AGAIN
            return buildOutSignal()
        }
    }

    private fun generateUnpredictableNumber() {
        addTagToDatabase("9F37", "76543210")
    }

    fun start(startProcessPayload: StartProcessPayload): OutSignal {
        syncData(startProcessPayload)
        if (!kernelDatabase.isPresent("84".decodeHex())) {
            errorIndication.l2 = ErrorIndication.L2.CARD_DATA_MISSING
            outcomeParameter.status = OutcomeParameter.Status.SELECT_NEXT
            outcomeParameter.start = OutcomeParameter.Start.B
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

        //TODO implement algorithm to generate number
        generateUnpredictableNumber()

        var outSignal = processFciResponse(startProcessPayload.fciResponse)
        if (outSignal != null) {
            return outSignal
        }

        val aip = kernelDatabase.getTlv(byteArrayOf(0x82.toByte()))!!.fullTag

        if (!aip.bytesValue[1].isBitSet(7)) {
            errorIndication.l2 = ErrorIndication.L2.CAM_FAILED
            return processErrorInGpo()
        }

        val aflValue = kernelDatabase.getTlv(byteArrayOf(0x94.toByte()))!!.fullTag
        val activeAfl = aflValue.bytesValue
        val readerContactlessTransactionLimit = getCtlessTrxLimit(aip)

        println(aip.hexValue)

        if (aip.bytesValue[1].isBitSet(0)) {
            //Supports RRP
            val rrpEntropy = kernelDatabase.getTlv("9F37")!!.fullTag.bytesValue
            outSignal = processRRP(rrpEntropy)
            if (outSignal != null) {
                return outSignal
            }
        } else {
            //Does not supports RRP
            tvr.relayResistancePerformed =
                TerminalVerificationResult.RelayResistancePerformed.RRP_NOT_SUPPORTED
        }

        if (aip.bytesValue[0].isBitSet(0) && terminalCapabilities.byte3.isBitSet(3)) {
            odaStatus = "CDA"
        } else {
            tvr.offlineDataAuthenticationWasNotPerformed = true

        }

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
                    userInterfaceRequestData.messageIdentifier =
                        MessageIdentifier.TRY_AGAIN
                    userInterfaceRequestData.status = UserInterfaceRequestData.Status.READY_TO_READ
                    userInterfaceRequestData.holdTime = 0


                    outcomeParameter.status = OutcomeParameter.Status.END_APPLICATION
                    outcomeParameter.start = OutcomeParameter.Start.B
                    errorIndication.l1 = ErrorIndication.L1.TRANSMISSION_ERROR
                    errorIndication.msgOnError = MessageIdentifier.TRY_AGAIN
                    return buildOutSignal()
                }
                if (!readRecordResponse.wasSuccessful()) {
                    userInterfaceRequestData.messageIdentifier = MessageIdentifier.TRY_ANOTHER_CARD
                    userInterfaceRequestData.status = UserInterfaceRequestData.Status.NOT_READY
                    outcomeParameter.status = OutcomeParameter.Status.END_APPLICATION
                    errorIndication.msgOnError = MessageIdentifier.TRY_ANOTHER_CARD
                    errorIndication.l2 = ErrorIndication.L2.STATUS_BYTES
                    errorIndication.sw1 = readRecordResponse.sw1
                    errorIndication.sw2 = readRecordResponse.sw2
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
                        userInterfaceRequestData.messageIdentifier =
                            MessageIdentifier.TRY_ANOTHER_CARD
                        userInterfaceRequestData.status = UserInterfaceRequestData.Status.NOT_READY
                        outcomeParameter.status = OutcomeParameter.Status.END_APPLICATION
                        outcomeParameter.uiRequestOnOutcomePresent = true
                        errorIndication.l2 = ErrorIndication.L2.PARSING_ERROR
                        errorIndication.msgOnError = MessageIdentifier.TRY_AGAIN
                        return buildOutSignal()
                    }
                }
                if (aflEntry.isSigned && odaStatus == "CDA") {
                    if (aflEntry.sfi <= 10) {
                        if (signedData.length < 2048 * 2) {
                            signedData += readRecordResponseData.getListAsByteArray().toHex()
                        } else {
                            tvr.cdaFailed = true
                        }
                    } else {
                        if (readRecordResponseData.getListAsByteArray()
                                .isNotEmpty() && readRecordResponseData.tag == BerTag(0x70) && signedData.length < 2048 * 2
                        ) {
                            signedData += readRecordResponseData.getFullAsByteArray()
                        } else {
                            tvr.cdaFailed = true
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

        val amountAuthorized = kernelDatabase.getTlv("9F02")?.fullTag?.intValue
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
            tvr.iccDataMissing = true
            tvr.cdaFailed = true
        } else {
            val caPkIndex = kernelDatabase.getTlv(BerTag(0x8F))!!.fullTag.hexValue!!
            val rid = "A000000004"
            val caPk = DataSets.caPublicKeys.find { it.index == caPkIndex && it.rId == rid }
            if (caPk == null) {
                tvr.cdaFailed = true
            }
            val sdaTag = kernelDatabase.getTlv(BerTag(0x9F, 0x4A))
            if (!(sdaTag != null && sdaTag.fullTag.intValue == 0x82)) {
                return buildOutSignal() // TODO corrigir 257 456.27.1
            }
            if (signedData.length < 2048 * 2) {
                signedData += sdaTag.fullTag.getFullAsByteArray().toHex()
            } else {
                tvr.cdaFailed = true
            }
        }

        val readerCvmRequired = kernelDatabase.getTlv("DF8126")!!.fullTag.intValue
        if (amountAuthorized > readerCvmRequired) {
            outcomeParameter.receipt = true
            terminalCapabilities.byte2 =
                kernelDatabase.getTlv("DF8118")!!.fullTag.intValue.toByte()
        } else {
            terminalCapabilities.byte2 =
                kernelDatabase.getTlv("DF8119")!!.fullTag.intValue.toByte()
        }

        //TODO pre-generate AC balance reading

        processingRestrictions()

        cvmSelection()

        val readerContactlessFloorLimit =
            kernelDatabase.getTlv("DF8123")!!.fullTag.intValue
        if (amountAuthorized > readerContactlessFloorLimit) {
            tvr.transactionExceedsFloorLimit = true
        }

        terminalActionAnalysis()

        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("95".decodeHex()), tvr.toByteArray()).buildTlv()
        )
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("9F33".decodeHex()), terminalCapabilities.toByteArray())
                .buildTlv()
        )
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("9F34".decodeHex()), cvmResult.toByteArray()).buildTlv()
        )

        println(acType)

        val genAcCommand: APDUCommand
        val cdol: BerTlv? = kernelDatabase.getTlv("8C")?.fullTag

        val cdolData = buildDolValue(cdol, false)

        var shouldRequestCda = false
        if (odaStatus == "CDA") {
            if (tvr.cdaFailed) {
                // CDA Failed
                val onDeviceCardholderVerificationSupported = aip.bytesValue.isBitSetOfByte(1, 0)
                if (onDeviceCardholderVerificationSupported) {
                    acType = ACType.AAC
                }
            } else {
                if (acType != ACType.AAC) {
                    shouldRequestCda = true
                } else {
                    val appCapInfo = kernelDatabase.getTlv("9F5D")?.fullTag?.bytesValue
                    val isCdaSupportedOver = appCapInfo != null && appCapInfo.isBitSetOfByte(0, 0)
                    if (isCdaSupportedOver) {
                        shouldRequestCda = true
                    }
                }
            }
        }
        genAcCommand = buildGenerateAc(acType, shouldRequestCda, cdolData)
        val genAcResponse: APDUResponse
        try {
            genAcResponse = communicateWithCard(genAcCommand)
        } catch (e: IOException) {
            //TODO
            userInterfaceRequestData.messageIdentifier = MessageIdentifier.TRY_AGAIN
            userInterfaceRequestData.status = UserInterfaceRequestData.Status.READY_TO_READ
            userInterfaceRequestData.holdTime = 0


            outcomeParameter.status = OutcomeParameter.Status.END_APPLICATION
            outcomeParameter.start = OutcomeParameter.Start.B
            errorIndication.l1 = ErrorIndication.L1.TRANSMISSION_ERROR
            errorIndication.msgOnError = MessageIdentifier.TRY_AGAIN
            return buildOutSignal()
        }
        if (!genAcResponse.wasSuccessful()) {
            userInterfaceRequestData.messageIdentifier = MessageIdentifier.TRY_ANOTHER_CARD
            userInterfaceRequestData.status = UserInterfaceRequestData.Status.NOT_READY
            outcomeParameter.status = OutcomeParameter.Status.END_APPLICATION
            errorIndication.msgOnError = MessageIdentifier.TRY_ANOTHER_CARD
            errorIndication.l2 = ErrorIndication.L2.STATUS_BYTES
            errorIndication.sw1 = genAcResponse.sw1
            errorIndication.sw2 = genAcResponse.sw2

            return buildOutSignal()
        }


        if (!genAcResponse.getParsedData().isEmpty() && genAcResponse.getParsedData().tag == BerTag(
                0x77
            )
        ) {
            kernelDatabase.parseAndStoreCardResponse(genAcResponse.getParsedData())
        } else {
            if (!genAcResponse.getParsedData()
                    .isEmpty() && genAcResponse.getParsedData().tag == BerTag(
                    0x80
                )
            ) {
                val responseMessageDataField = genAcResponse.getParsedData().bytesValue
                val parseNOK = responseMessageDataField.size < 11 ||
                        responseMessageDataField.size > 43 ||
                        kernelDatabase.isPresent("9F27".decodeHex()) ||
                        kernelDatabase.isPresent("9F36".decodeHex()) ||
                        kernelDatabase.isPresent("9F26".decodeHex()) ||
                        (responseMessageDataField.size > 11 && kernelDatabase.isPresent("9F10".decodeHex()))
                if (parseNOK) {
                    return buildOutSignal()
                }
                kernelDatabase.add(
                    BerTlvBuilder().addBytes(
                        BerTag("9F27".decodeHex()),
                        byteArrayOf(responseMessageDataField[0])
                    ).buildTlv()
                )
                kernelDatabase.add(
                    BerTlvBuilder().addBytes(
                        BerTag("9F36".decodeHex()),
                        responseMessageDataField.copyOfRange(1, 3)
                    ).buildTlv()
                )
                kernelDatabase.add(
                    BerTlvBuilder().addBytes(
                        BerTag("9F26".decodeHex()),
                        responseMessageDataField.copyOfRange(3, 11)
                    ).buildTlv()
                )
                if (responseMessageDataField.size > 11) {
                    kernelDatabase.add(
                        BerTlvBuilder().addBytes(
                            BerTag("9F10".decodeHex()),
                            responseMessageDataField.copyOfRange(11, responseMessageDataField.size)
                        ).buildTlv()
                    )
                }
            }
        }

        if (!kernelDatabase.isPresent("9F27".decodeHex()) || !kernelDatabase.isPresent("9F36".decodeHex())) {
            return buildOutSignal()
        }
        val cid = kernelDatabase.getTlv("9F27")!!.fullTag
        val shouldContinue = (cid.bytesValue[0].and(0xC0.toByte()) == 0x40.toByte()) ||
                (cid.bytesValue[0].and(0xC0.toByte()) == 0x80.toByte() && (acType == ACType.TC || acType == ACType.ARQC)) ||
                (cid.bytesValue[0].and(0xC0.toByte()) == 0x00.toByte())
        if (!shouldContinue) {
            return buildOutSignal()
        }
        userInterfaceRequestData.messageIdentifier =
            MessageIdentifier.NO_MESSAGE// 0b11110 //MID = clear display
        userInterfaceRequestData.status =
            UserInterfaceRequestData.Status.CARD_READ_SUCCESSFULLY//0b100 // status = card read successfully
        userInterfaceRequestData.holdTime = 0b0 // Hold time = 0

        if (kernelDatabase.isPresent("9F4B".decodeHex())) {
            //S910.1
            val caPkIndex = kernelDatabase.getTlv(BerTag(0x8F))!!.fullTag.hexValue!!
            val rid = "A000000004"
            val caPk = DataSets.caPublicKeys.find { it.index == caPkIndex && it.rId == rid }
                ?: return buildOutSignal()
            //Perform CDA
            val ICCPKOk = true
            if (!ICCPKOk) {
                tvr.cdaFailed = true
                return buildOutSignal()
            }
            val rrpPerformed =
                tvr.relayResistancePerformed == TerminalVerificationResult.RelayResistancePerformed.RRP_PERFORMED
            if (rrpPerformed) {
                //S910.4.1
                //TODO Verify SDAD EMV Book2 Section 6.6
                val iccDynamicData = ByteArray(45)
                val iccDynamicNumberLength = iccDynamicData[0]
                if (iccDynamicData.size < (44 + iccDynamicNumberLength)) {
                    tvr.cdaFailed = true
                    return buildOutSignal()
                }
                val iccDynamicNumber = iccDynamicData.copyOfRange(1, 1 + iccDynamicNumberLength)
                val applicationCryptogram = iccDynamicData.copyOfRange(
                    2 + iccDynamicNumberLength,
                    10 + iccDynamicNumberLength
                )
                kernelDatabase.add(
                    BerTlvBuilder().addBytes(
                        BerTag("9F4C".decodeHex()),
                        iccDynamicNumber
                    ).buildTlv()
                )
                kernelDatabase.add(
                    BerTlvBuilder().addBytes(
                        BerTag("9F26".decodeHex()),
                        applicationCryptogram
                    ).buildTlv()
                )

                val terminalRelayResistanceEntropy =
                    kernelDatabase.getTlv("DF8301")!!.fullTag.bytesValue
                val deviceRelayResistanceEntropy =
                    kernelDatabase.getTlv("DF8302")!!.fullTag.bytesValue
                val minTimeForProcessingApdu =
                    kernelDatabase.getTlv("DF8303")!!.fullTag.bytesValue
                val maxTimeForProcessingApdu =
                    kernelDatabase.getTlv("DF8304")!!.fullTag.bytesValue
                val deviceEstimatedTimeForResponse =
                    kernelDatabase.getTlv("DF8305")!!.fullTag.bytesValue

                val cdaTerminalRelayResistanceEntropy = iccDynamicData.copyOfRange(
                    30 + iccDynamicNumberLength,
                    30 + iccDynamicNumberLength + 4
                )
                val cdaDeviceRelayResistanceEntropy = iccDynamicData.copyOfRange(
                    34 + iccDynamicNumberLength,
                    34 + iccDynamicNumberLength + 4
                )
                val cdaMinTimeForProcessingApdu = iccDynamicData.copyOfRange(
                    38 + iccDynamicNumberLength,
                    38 + iccDynamicNumberLength + 2
                )
                val cdaMaxTimeForProcessingApdu = iccDynamicData.copyOfRange(
                    40 + iccDynamicNumberLength,
                    40 + iccDynamicNumberLength + 2
                )
                val cdaDeviceEstimatedTimeForResponse = iccDynamicData.copyOfRange(
                    42 + iccDynamicNumberLength,
                    42 + iccDynamicNumberLength + 2
                )
                if (!terminalRelayResistanceEntropy.contentEquals(cdaTerminalRelayResistanceEntropy) ||
                    !deviceRelayResistanceEntropy.contentEquals(cdaDeviceRelayResistanceEntropy) ||
                    !minTimeForProcessingApdu.contentEquals(cdaMinTimeForProcessingApdu) ||
                    !maxTimeForProcessingApdu.contentEquals(cdaMaxTimeForProcessingApdu) ||
                    !deviceEstimatedTimeForResponse.contentEquals(cdaDeviceEstimatedTimeForResponse)
                ) {
                    tvr.cdaFailed = true
                    return buildOutSignal()
                }
            } else {
                //S910.4.0
                //TODO Verify SDAD EMV Book2 Section 6.6
                val iccDynamicData = ByteArray(30)
                val iccDynamicNumberLength = iccDynamicData[0]
                if (iccDynamicData.size < (30 + iccDynamicNumberLength)) {
                    tvr.cdaFailed = true
                    return buildOutSignal()
                }
                val iccDynamicNumber = iccDynamicData.copyOfRange(1, 1 + iccDynamicNumberLength)
                val applicationCryptogram = iccDynamicData.copyOfRange(
                    2 + iccDynamicNumberLength,
                    10 + iccDynamicNumberLength
                )
                kernelDatabase.add(
                    BerTlvBuilder().addBytes(
                        BerTag("9F4C".decodeHex()),
                        iccDynamicNumber
                    ).buildTlv()
                )
                kernelDatabase.add(
                    BerTlvBuilder().addBytes(
                        BerTag("9F26".decodeHex()),
                        applicationCryptogram
                    ).buildTlv()
                )
            }
        } else {
            //S910.30
            val applicationCryptogram = kernelDatabase.getTlv("9F26")?.fullTag
            if (applicationCryptogram == null) {
                tvr.cdaFailed = true
                return buildOutSignal()
            }
            if (applicationCryptogram.bytesValue[0].and(0xC0.toByte()) != 0x00.toByte()) {
                //34
                if (shouldRequestCda) {
                    tvr.cdaFailed = true
                    return buildOutSignal()
                }
                val rrpPerformed =
                    tvr.relayResistancePerformed == TerminalVerificationResult.RelayResistancePerformed.RRP_PERFORMED
                if (rrpPerformed) {
                    //TODO S910.39
                }

            }
        }
        outcomeParameter.dataRecordPresent = true
        val PCII = kernelDatabase.getTlv("DF4B")?.fullTag?.bytesValue
        if (PCII != null && !PCII.and(byteArrayOf(0x00, 0x03, 0x0F)).contentEquals(ByteArray(3))) {
            //S72
            outcomeParameter.status =
                OutcomeParameter.Status.END_APPLICATION //0b100 // END_APPLICATION
            outcomeParameter.start = OutcomeParameter.Start.B//0b1 // B
            val phoneMessageTable = kernelDatabase.getTlv("DF8131")!!.fullTag.bytesValue
            for (phoneMessageEntry in phoneMessageTable.toList().chunked(8)) {
                val pciiMask = phoneMessageEntry.subList(0, 3).toByteArray()
                val pciiValue = phoneMessageEntry.subList(3, 6).toByteArray()
                val messageId = phoneMessageEntry.subList(6, 7).toByteArray()
                val status = phoneMessageEntry.subList(7, 8).toByteArray()
                if (pciiMask.and(PCII).contentEquals(pciiValue)) {
                    userInterfaceRequestData.messageIdentifier =
                        MessageIdentifier.from(messageId[0])
                    userInterfaceRequestData.status =
                        UserInterfaceRequestData.Status.from(status[0])
                    userInterfaceRequestData.holdTime = 0x0
                    break
                }
            }
        } else {
            //S74
            if (cid.bytesValue[0].and(0xC0.toByte()) == 0x40.toByte()) {
                outcomeParameter.status = OutcomeParameter.Status.APPROVED // 0b1 // APPROVED
            } else {
                if (cid.bytesValue[0].and(0xC0.toByte()) == 0x80.toByte()) {
                    outcomeParameter.status =
                        OutcomeParameter.Status.ONLINE_REQUEST // 0b11 // Online Request
                } else {
                    outcomeParameter.status = OutcomeParameter.Status.DECLINED // 0b10 // Declined
                }
            }
            userInterfaceRequestData.status =
                UserInterfaceRequestData.Status.NOT_READY // 0x0 // NOT READY
            if (cid.bytesValue[0].and(0xC0.toByte()) == 0x40.toByte()) {
                if (outcomeParameter.cvm == OutcomeParameter.Cvm.OBTAIN_SIGNATURE) {
                    userInterfaceRequestData.messageIdentifier =
                        MessageIdentifier.APPROVED_SIGN//0b11010 // APProved_sign
                } else {
                    userInterfaceRequestData.messageIdentifier =
                        MessageIdentifier.APPROVED // 0b11 // APProved_sign
                }
            } else {
                if (cid.bytesValue[0].and(0xC0.toByte()) == 0x80.toByte()) {
                    userInterfaceRequestData.messageIdentifier =
                        MessageIdentifier.AUTHORISING_PLEASE_WAIT // 0b11011 // authorising_wait
                } else {
                    userInterfaceRequestData.messageIdentifier =
                        MessageIdentifier.NOT_AUTHORIZED//0b111 // authorising_wait
                }
            }
        }
        if (PCII != null && !PCII.and(byteArrayOf(0x00, 0x03, 0x0F)).contentEquals(ByteArray(3))) {
            outcomeParameter.uiRequestOnRestartPresent = true

        } else {
            outcomeParameter.uiRequestOnOutcomePresent = true
        }
        //Generate ARQC
        return buildOutSignal()
    }

    private fun terminalActionAnalysis() {
        println("terminalActionAnalysis")
        val IACDefault = kernelDatabase.getTlv("9F0D")?.fullTag?.bytesValue
            ?: "FFFFFFFFFC".decodeHex()
        val IACDenial =
            kernelDatabase.getTlv("9F0E")?.fullTag?.bytesValue ?: ByteArray(5)
        val IACOnline = kernelDatabase.getTlv("9F0F")?.fullTag?.bytesValue
            ?: "FFFFFFFFFC".decodeHex()
        val TACDefault = kernelDatabase.getTlv("DF8120")!!.fullTag.bytesValue
        val TACDenial = kernelDatabase.getTlv("DF8121")!!.fullTag.bytesValue
        val TACOnline = kernelDatabase.getTlv("DF8122")!!.fullTag.bytesValue
        val terminalType = kernelDatabase.getTlv("9F35")!!.fullTag.hexValue

        if (!TACDenial.or(IACDenial).and(tvr.toByteArray()).contentEquals(ByteArray(5))) {
            acType = ACType.AAC
            return
        }
        val terminalIsOnlineOnly =
            terminalType == "11" || terminalType == "21" || terminalType == "14" || terminalType == "24" || terminalType == "34"
        if (terminalIsOnlineOnly) {
            acType = ACType.ARQC
            return
        }
        val terminalIsOfflineOnly =
            terminalType == "23" || terminalType == "26" || terminalType == "36" || terminalType == "13" || terminalType == "16"
        if (terminalIsOfflineOnly) {
            if (TACDefault.or(IACDefault).and(tvr.toByteArray()).contentEquals(ByteArray(5))) {
                acType = ACType.TC
                return
            } else {
                acType = ACType.AAC
                return
            }
        }
        if (TACOnline.or(IACOnline).and(tvr.toByteArray()).contentEquals(ByteArray(5))) {
            acType = ACType.TC
            return
        } else {
            acType = ACType.ARQC
            return
        }
    }

    private fun cvmSelection() {
        println("cvmSelection")
        val aip = kernelDatabase.getTlv(byteArrayOf(0x82.toByte()))!!.fullTag.bytesValue
        val supportsCDCVM = aip.isBitSetOfByte(1, 0)
        val amountAuthorized = kernelDatabase.getTlv("9F02")!!.fullTag.intValue
        val amountCashback = kernelDatabase.getTlv("9F03")?.fullTag?.intValue
        val readerCvmRequired = kernelDatabase.getTlv("DF8126")!!.fullTag.intValue
        if (supportsCDCVM) {
            //TODO add kernel configurability
            if (readerCvmRequired > amountAuthorized) {
                cvmResult.cvmPerformed = CvmResults.CvmCode.PLAINTEXT_OFFLINE_PIN
                cvmResult.cvmCondition = CvmResults.CvmCondition.ALWAYS
                cvmResult.cvmResult = CvmResults.CvmResult.SUCCESSFUL
                outcomeParameter.cvm = OutcomeParameter.Cvm.NO_CVM
            } else {
                cvmResult.cvmPerformed = CvmResults.CvmCode.NO_CVM
                cvmResult.cvmCondition = CvmResults.CvmCondition.ALWAYS
                cvmResult.cvmResult = CvmResults.CvmResult.SUCCESSFUL
                outcomeParameter.cvm = OutcomeParameter.Cvm.CONFIRMATION_CODE_VERIFIED
            }
            return
        }
        if (!aip.isBitSetOfByte(4, 0)) {
            cvmResult.cvmPerformed = CvmResults.CvmCode.NO_CVM
            cvmResult.cvmCondition = CvmResults.CvmCondition.ALWAYS
            cvmResult.cvmResult = CvmResults.CvmResult.UNKNOWN
            outcomeParameter.cvm = OutcomeParameter.Cvm.NO_CVM
            return
        }
        val cvmList = kernelDatabase.getTlv("8E")?.fullTag?.bytesValue
        if (!(cvmList != null && cvmList.isNotEmpty())) {
            cvmResult.cvmPerformed = CvmResults.CvmCode.NO_CVM
            cvmResult.cvmCondition = CvmResults.CvmCondition.ALWAYS
            cvmResult.cvmResult = CvmResults.CvmResult.UNKNOWN
            outcomeParameter.cvm = OutcomeParameter.Cvm.NO_CVM
            tvr.iccDataMissing = true
            return
        }
        val xAmount = cvmList.copyOfRange(0, 4).getIntValue()
        val yAmount = cvmList.copyOfRange(4, 8).getIntValue()
        val transactionType = kernelDatabase.getTlv("9C")!!.fullTag.hexValue
        var cvRuleOffset = 8
        val issuerCountryCode = kernelDatabase.getTlv("5F28")?.fullTag?.hexValue
            ?: return

        val terminalCountryCode = kernelDatabase.getTlv("9F1A")!!.fullTag.hexValue
        val isSameCountry = terminalCountryCode == issuerCountryCode
        val terminalType = kernelDatabase.getTlv("9F35")!!.fullTag.hexValue

        while (cvRuleOffset < cvmList.size) {
            val cvRule = cvmList.copyOfRange(cvRuleOffset, cvRuleOffset + 2)
            cvRuleOffset += 2
            val cvCondition = CvmResults.CvmCondition.from(cvRule[1])
            val cvCode = cvRule[0]
            val cvRuleShouldContinue = cvCode.isBitSet(6)
            cvmResult.shouldApplyNext = cvRuleShouldContinue
            val cvmCode = CvmResults.CvmCode.from(cvCode.and(0b00111111))
            if (cvCondition != null) {
                val conditionSatisfied = shouldApplyCondition(
                    cvmCode,
                    cvCondition,
                    xAmount,
                    yAmount,
                    amountAuthorized,
                    amountCashback,
                    isSameCountry,
                    terminalCapabilities,
                    transactionType,
                    terminalType
                )
                if (conditionSatisfied) {
                    if (cvmCode == null) {
                        tvr.unrecognizedCvm = true
                        if (cvRuleShouldContinue) {
                            continue
                        }
                        outcomeParameter.cvm = OutcomeParameter.Cvm.NO_CVM
                        tvr.cardholderVerificationWasNotSuccessful = true
                        if (cvmCode == CvmResults.CvmCode.FAIL_CVM) {
                            cvmResult.shouldApplyNext = cvRuleShouldContinue
                            cvmResult.cvmPerformed = CvmResults.CvmCode.from(cvmCode)!!
                            cvmResult.cvmCondition = CvmResults.CvmCondition.from(cvRule[1])!!
                            cvmResult.cvmResult = CvmResults.CvmResult.FAILED
                        } else {
                            cvmResult.cvmPerformed = CvmResults.CvmCode.NO_CVM
                            cvmResult.cvmCondition = CvmResults.CvmCondition.ALWAYS
                            cvmResult.cvmResult = CvmResults.CvmResult.FAILED
                        }
                        return
                    }
                    //TODO CVM.17
                    val isCvmCodeSupported = terminalCapabilities.supportsCvmCode(cvmCode)
                    if (isCvmCodeSupported && cvmCode.value.and(0x3F.toByte()) != 0.toByte()) {
                        //CVM.18
                        cvmResult.cvmPerformed = CvmResults.CvmCode.from(cvRule[0].and(0b111111))!!
                        cvmResult.cvmCondition = CvmResults.CvmCondition.from(cvRule[1])!!
                        if (cvmCode == CvmResults.CvmCode.ENCIPHERED_ONLINE_PIN) {
                            outcomeParameter.cvm = OutcomeParameter.Cvm.ONLINE_PIN
                            cvmResult.cvmResult = CvmResults.CvmResult.UNKNOWN
                            tvr.onlinePinEntered = true
                            return
                        }
                        if (cvmCode == CvmResults.CvmCode.SIGNATURE) {
                            outcomeParameter.cvm = OutcomeParameter.Cvm.OBTAIN_SIGNATURE
                            outcomeParameter.receipt = true
                            cvmResult.cvmResult = CvmResults.CvmResult.UNKNOWN
                            return
                        }
                        if (cvmCode == CvmResults.CvmCode.NO_CVM) {
                            outcomeParameter.cvm = OutcomeParameter.Cvm.NO_CVM
                            cvmResult.cvmResult = CvmResults.CvmResult.SUCCESSFUL
                            return
                        }
                    } else {
                        if (cvRuleShouldContinue) {
                            continue
                        }
                        outcomeParameter.cvm = OutcomeParameter.Cvm.NO_CVM
                        tvr.cardholderVerificationWasNotSuccessful = true
                        //tvr.setBitOfByte(7, 2) // Cardholder verification was not successful
                        if (cvmCode == CvmResults.CvmCode.FAIL_CVM) {
                            cvmResult.cvmPerformed = CvmResults.CvmCode.from(cvRule[0])!!
                            cvmResult.cvmCondition = CvmResults.CvmCondition.from(cvRule[1])!!
                            cvmResult.cvmResult = CvmResults.CvmResult.FAILED
                        } else {
                            cvmResult.cvmPerformed = CvmResults.CvmCode.NO_CVM
                            cvmResult.cvmCondition = CvmResults.CvmCondition.ALWAYS
                            cvmResult.cvmResult = CvmResults.CvmResult.FAILED
                        }
                        return
                    }
                }

            }
        }
        cvmResult.cvmPerformed = CvmResults.CvmCode.NO_CVM
        cvmResult.cvmCondition = CvmResults.CvmCondition.ALWAYS
        cvmResult.cvmResult = CvmResults.CvmResult.FAILED
        outcomeParameter.cvm = OutcomeParameter.Cvm.NO_CVM
        tvr.cardholderVerificationWasNotSuccessful = true
        //tvr.setBitOfByte(7, 2) // Cardholder verification was not successful
        return
    }

    private fun shouldApplyCondition(
        cvm: CvmResults.CvmCode?,
        condition: CvmResults.CvmCondition,
        xValue: Int,
        yValue: Int,
        amount: Int,
        cashbackAmount: Int?,
        isSameCurrency: Boolean,
        terminalCapabilities: TerminalCapabilities,
        transactionType: String,
        terminalType: String?
    ): Boolean {
        val isUnattended = listOf("4", "5", "6").contains(terminalType?.get(1)!!.toString())
        val isManual = terminalCapabilities.byte1.and(0b10000000.toByte()) == 0b10000000.toByte()
        val isCash = transactionType.decodeHex()[0].and(0b10000000.toByte()) == 0b10000000.toByte()
        val hasCashback = cashbackAmount != null && cashbackAmount > 0
        val isPurchase = !isCash
        return when (condition) {
            CvmResults.CvmCondition.ALWAYS -> true
            CvmResults.CvmCondition.IF_UNATTENDED_CASH -> isUnattended && isCash
            CvmResults.CvmCondition.IF_NOT_UNATTENDED_CASH_NOT_MANUAL_NOT_PURCHASE_WITH_CASHBACK -> (!(isUnattended && isCash) && !(isManual) && !(isPurchase && hasCashback))
            CvmResults.CvmCondition.IF_TERMINAL_SUPPORTS -> cvm != null && terminalCapabilities.supportsCvmCode(
                cvm
            )
            CvmResults.CvmCondition.IF_MANUAL_CASH -> isManual && isCash
            CvmResults.CvmCondition.IF_PURCHASE_WITH_CASHBACK -> hasCashback
            CvmResults.CvmCondition.IF_ON_CURRENCY_UNDER_X -> isSameCurrency && amount < xValue
            CvmResults.CvmCondition.IF_ON_CURRENCY_OVER_X -> isSameCurrency && amount > xValue
            CvmResults.CvmCondition.IF_ON_CURRENCY_UNDER_Y -> isSameCurrency && amount < yValue
            CvmResults.CvmCondition.IF_ON_CURRENCY_OVER_Y -> isSameCurrency && amount > yValue
        }
    }

    private fun validateAppVersionNumber() {
        val cardApplicationVersionNumber =
            kernelDatabase.getTlv("9F08")?.fullTag?.hexValue
        if (cardApplicationVersionNumber != null) {
            val terminalApplicationVersionNumber =
                kernelDatabase.getTlv("9F09")?.fullTag?.hexValue
            if (cardApplicationVersionNumber != terminalApplicationVersionNumber) {
                tvr.iccAndTerminalHaveDifferentApplicationVersions = true
                //tvr.setBitOfByte(7, 1) // ICC and terminal have different application versions
            }
        }
    }

    private fun validateExpirationDate() {
        val appEffectiveDate = kernelDatabase.getTlv("5F25")?.fullTag?.hexValue
        val appExpirationDate = kernelDatabase.getTlv("5F24")!!.fullTag.hexValue
        val transactionDate = kernelDatabase.getTlv("9A")!!.fullTag.hexValue
        if (appEffectiveDate != null && appEffectiveDate > transactionDate) {
            //tvr.setBitOfByte(5, 1) // App not yet effective
            tvr.applicationNotYetEffective = true
        }
        if (transactionDate > appExpirationDate) {
            tvr.expiredApplication = true
            //tvr.setBitOfByte(6, 1) // App expired
        }
    }

    private fun validateAtmUsage(): Boolean {
        val appUsageControl =
            kernelDatabase.getTlv("9F07")?.fullTag?.bytesValue ?: return false
        val terminalType = kernelDatabase.getTlv("9F35")!!.fullTag.hexValue
        val additionalTerminalCapabilities =
            kernelDatabase.getTlv("9F40")!!.fullTag.bytesValue

        val canBeCash = additionalTerminalCapabilities.isBitSetOfByte(7, 0)

        val isAtm = listOf("14", "15", "16").contains(terminalType)

        if (isAtm && canBeCash) {
            if (!appUsageControl.isBitSetOfByte(1, 1)) {
                tvr.requestedServiceNotAllowedForCardProduct = true
                return false
            }
        } else {
            if (!appUsageControl.isBitSetOfByte(0, 0)) {
                tvr.requestedServiceNotAllowedForCardProduct = true
                return false
            }
        }
        return true
    }

    private fun validateNationalInternationalTransaction(
        typeCheck: Boolean,
        isSameCountry: Boolean,
        auc: ByteArray,
        nacAucBit: Int,
        nacAucByte: Int,
        intAucBit: Int,
        intAucByte: Int
    ) {
        if (typeCheck) {
            var aucCheckBit = nacAucBit
            var aucCheckByte = nacAucByte
            if (!isSameCountry) {
                aucCheckBit = intAucBit
                aucCheckByte = intAucByte
            }
            if (!auc.isBitSetOfByte(aucCheckBit, aucCheckByte)) {
                tvr.requestedServiceNotAllowedForCardProduct = true
            }
        }

    }

    private fun validateAppUsageControl() {

        val appUsageControl =
            kernelDatabase.getTlv("9F07")?.fullTag?.bytesValue ?: return

        val issuerCountryCode = kernelDatabase.getTlv("5F28")?.fullTag?.hexValue ?: return

        val terminalCountryCode = kernelDatabase.getTlv("9F1A")!!.fullTag.hexValue

        val transactionType = kernelDatabase.getTlv("9C")!!.fullTag.hexValue

        val isCashTransaction = transactionType == "01" || transactionType == "17"
        val isSameCountry = terminalCountryCode == issuerCountryCode

        //Check for cashTransaction
        validateNationalInternationalTransaction(
            isCashTransaction, isSameCountry, appUsageControl,
            7, 0, 6, 0
        )

        val isPurchaseTransaction = transactionType == "00" || transactionType == "09"

        //Check for goods
        validateNationalInternationalTransaction(
            isPurchaseTransaction, isSameCountry, appUsageControl,
            5, 0, 4, 0
        )

        //Check for services
        validateNationalInternationalTransaction(
            isPurchaseTransaction, isSameCountry, appUsageControl,
            3, 0, 2, 0
        )

        val cashbackAmount = kernelDatabase.getTlv("9F03")?.fullTag?.intValue
        val hasCashback = cashbackAmount != null && cashbackAmount > 0

        //Check for cashback
        validateNationalInternationalTransaction(
            hasCashback, isSameCountry, appUsageControl,
            7, 1, 6, 1
        )

    }

    private fun processingRestrictions() {
        println("processingRestrictions")
        validateAppVersionNumber()
        validateExpirationDate()
        if (!validateAtmUsage()) {
            return
        }
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

    private fun buildDolValue(requestedDol: BerTlv?, shouldAddTemplate: Boolean = true): ByteArray {
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
            fullPdol = if (shouldAddTemplate) {
                BerTlvBuilder().addBytes(BerTag(0x83), pdolValue).buildArray()
            } else {
                pdolValue
            }
        } else {
            fullPdol = byteArrayOf(0x83.toByte(), 0x00)
        }
        return fullPdol
    }

    data class OutcomeParameter(
        var status: Status = Status.N_A,
        var start: Start = Start.N_A,
        val onlineResponseData: Int = 0,
        var cvm: Cvm = Cvm.N_A,
        var uiRequestOnOutcomePresent: Boolean = false,
        var uiRequestOnRestartPresent: Boolean = false,
        var dataRecordPresent: Boolean = false,
        val discretionaryDataPresent: Boolean = false,
        var receipt: Boolean = false,
        val alternateInterfacePreference: Int = 0,
        var fieldOffRequest: Int = 0,
        val removalTimeout: Int = 0,

        ) : IByteable {
        override fun toByteArray(): ByteArray {
            TODO("Not yet implemented")
        }

        enum class Status(var value: Byte) {
            APPROVED(0b00010000),
            DECLINED(0b00100000),
            ONLINE_REQUEST(0b00110000),
            END_APPLICATION(0b01000000),
            SELECT_NEXT(0b01010000),
            TRY_ANOTHER_INTERFACE(0b1100000),
            TRY_AGAIN(0b1110000),
            N_A(0b11110000.toByte());

            companion object {
                fun from(findValue: Byte): Status = values().first { it.value == findValue }
            }
        }

        enum class Start(var value: Byte) {
            A(0b00000000),
            B(0b00010000),
            C(0b00100000),
            D(0b00110000),
            N_A(0b11110000.toByte());

            companion object {
                fun from(findValue: Byte): Start = values().first { it.value == findValue }
            }
        }

        enum class Cvm(var value: Byte) {
            NO_CVM(0b00000000),
            OBTAIN_SIGNATURE(0b00010000),
            ONLINE_PIN(0b00100000),
            CONFIRMATION_CODE_VERIFIED(0b00110000),
            N_A(0b11110000.toByte());

            companion object {
                fun from(findValue: Byte): Cvm = values().first { it.value == findValue }
            }
        }
    }

    class DataRecord {
        var data: MutableList<BerTlv> = ArrayList()
        fun add(tag: BerTlv) {
            data.add(tag)
        }

        override fun toString(): String {
            return data.joinToString { it.getFullAsByteArray().toHex() }
        }
    }

    class DiscretionaryData {
        var data: MutableList<BerTlv> = ArrayList()
        fun add(tag: BerTlv) {
            data.add(tag)
        }

        override fun toString(): String {
            return data.joinToString { it.getFullAsByteArray().toHex() }
        }
    }

    data class TerminalCapabilities(
        var byte1: Byte = 0,
        var byte2: Byte = 0,
        var byte3: Byte = 0,
    ) : IByteable {

        fun supportsCvmCode(code: CvmResults.CvmCode): Boolean {
            return when (code) {
                CvmResults.CvmCode.FAIL_CVM -> true
                CvmResults.CvmCode.FAIL_CVM_2 -> true
                CvmResults.CvmCode.PLAINTEXT_OFFLINE_PIN -> supportsOfflinePlaintextPin()
                CvmResults.CvmCode.ENCIPHERED_ONLINE_PIN -> supportsOnlineEncipheredPin()
                CvmResults.CvmCode.PLAINTEXT_OFFLINE_PIN_AND_SIGNATURE -> supportsOfflinePlaintextPin() && supportsSignature()
                CvmResults.CvmCode.ENCIPHERED_OFFLINE_PIN -> supportsOfflineEncipheredPin()
                CvmResults.CvmCode.ENCIPHERED_OFFLINE_PIN_AND_SIGNATURE -> supportsOfflineEncipheredPin() && supportsSignature()
                CvmResults.CvmCode.SIGNATURE -> supportsSignature()
                CvmResults.CvmCode.NO_CVM -> supportsNoCvm()
            }
        }

        private fun supportsOfflinePlaintextPin(): Boolean {
            return byte2.isBitSet(7)

        }

        private fun supportsOnlineEncipheredPin(): Boolean {
            return byte2.isBitSet(6)
        }

        private fun supportsSignature(): Boolean {
            return byte2.isBitSet(5)
        }

        private fun supportsOfflineEncipheredPin(): Boolean {
            return byte2.isBitSet(4)
        }

        private fun supportsNoCvm(): Boolean {
            return byte2.isBitSet(3)
        }

        override fun toByteArray(): ByteArray {
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

    enum class ACType(var value: Byte) {
        AAC(0b00000000),
        TC(0b01000000),
        ARQC(0b10000000.toByte()),
    }

    data class CvmResults(
        var shouldApplyNext: Boolean = false,
        var cvmPerformed: CvmCode = CvmCode.FAIL_CVM,
        var cvmCondition: CvmCondition = CvmCondition.ALWAYS,
        var cvmResult: CvmResult = CvmResult.UNKNOWN,
    ) : IByteable {

        enum class CvmCode(val value: Byte) {
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
                fun from(value: Byte) = values().firstOrNull { it.value == value }

            }
        }

        enum class CvmCondition(val value: Byte) {
            ALWAYS(0x00),
            IF_UNATTENDED_CASH(0x01),
            IF_NOT_UNATTENDED_CASH_NOT_MANUAL_NOT_PURCHASE_WITH_CASHBACK(0x02),
            IF_TERMINAL_SUPPORTS(0x03),
            IF_MANUAL_CASH(0x04),
            IF_PURCHASE_WITH_CASHBACK(0x05),
            IF_ON_CURRENCY_UNDER_X(0x06),
            IF_ON_CURRENCY_OVER_X(0x07),
            IF_ON_CURRENCY_UNDER_Y(0x08),
            IF_ON_CURRENCY_OVER_Y(0x09);

            companion object {
                fun from(value: Byte) = values().firstOrNull { it.value == value }
            }
        }

        enum class CvmResult(val value: Byte) {
            UNKNOWN(0x00),
            FAILED(0x01),
            SUCCESSFUL(0x02);

            companion object {
                fun from(findValue: Byte): CvmResult = values().first { it.value == findValue }
            }
        }

        override fun toByteArray(): ByteArray {
            if (shouldApplyNext) {
                return byteArrayOf(
                    cvmPerformed.value.or(0b1000000),
                    cvmCondition.value,
                    cvmResult.value
                )
            }
            return byteArrayOf(cvmPerformed.value, cvmCondition.value, cvmResult.value)
        }
    }

    data class ErrorIndication(
        var l1: L1 = L1.OK,
        var l2: L2 = L2.OK,
        val l3: L3 = L3.OK,
        var sw1: Byte = 0,
        var sw2: Byte = 0,
        var msgOnError: MessageIdentifier = MessageIdentifier.N_A,
    ) : IByteable {


        override fun toByteArray(): ByteArray {
            return byteArrayOf(l1.value, l2.value, l3.value, sw1, sw2, msgOnError.value)
        }

        enum class L1(val value: Byte) {
            OK(0x0),
            TIME_OUT_ERROR(0b1),
            TRANSMISSION_ERROR(0b10),
            PROTOCOL_ERROR(0b11);

            companion object {
                fun from(findValue: Byte): L1 = values().first { it.value == findValue }
            }
        }

        enum class L2(val value: Byte) {
            OK(0x0),
            CARD_DATA_MISSING(0b1),
            CAM_FAILED(0b10),
            STATUS_BYTES(0b11),
            PARSING_ERROR(0b100),
            MAX_LIMIT_EXCEEDED(0b101),
            CARD_DATA_ERROR(0b110),
            MAGSTRIPE_NOT_SUPPORTED(0b1000),
            NO_PPSE(0b1001),
            EMPTY_CANDIDATE_LIST(0b1010),
            IDS_READ_ERROR(0b1011),
            IDS_WRITE_ERROR(0b1100),
            IDS_DATA_ERROR(0b1101),
            IDS_NO_MATCHING_AC(0b1110),
            TERMINAL_DATA_ERROR(0b1111);

            companion object {
                fun from(findValue: Byte): L2 = values().first { it.value == findValue }
            }
        }

        enum class L3(val value: Byte) {
            OK(0),
            TIME_OUT(0b1),
            STOP(0b10),
            AMOUNT_NOT_PRESENT(0B11);

            companion object {
                fun from(findValue: Byte): L3 = values().first { it.value == findValue }
            }
        }
    }

    data class TerminalVerificationResult(
        var offlineDataAuthenticationWasNotPerformed: Boolean = false,
        val sdaFailed: Boolean = false,
        var iccDataMissing: Boolean = false,
        val cardAppearsOnTerminalExceptionFile: Boolean = false,
        val ddaFailed: Boolean = false,
        var cdaFailed: Boolean = false,
        var iccAndTerminalHaveDifferentApplicationVersions: Boolean = false,
        var expiredApplication: Boolean = false,
        var applicationNotYetEffective: Boolean = false,
        var requestedServiceNotAllowedForCardProduct: Boolean = false,
        val newCard: Boolean = false,
        var cardholderVerificationWasNotSuccessful: Boolean = false,
        var unrecognizedCvm: Boolean = false,
        val pinTryLimitExceeded: Boolean = false,
        val pinEntryRequiredAndPinpadNotPresentOrNotWorking: Boolean = false,
        val pinEntryRequiredPinpadPresentButPinNotEntered: Boolean = false,
        var onlinePinEntered: Boolean = false,
        var transactionExceedsFloorLimit: Boolean = false,
        val lowerConsecutiveOfflineLimitExceeded: Boolean = false,
        val upperConsecutiveOfflineLimitExceeded: Boolean = false,
        val transactionSelectedRandomlyForOnlineProcessing: Boolean = false,
        val merchantForcedTransactionOnline: Boolean = false,
        val defaultTdolUsed: Boolean = false,
        val issuerAuthenticationFailed: Boolean = false,
        val scriptProcessingFailedBeforeFinalGenAc: Boolean = false,
        val scriptProcessingFailedAfterFinalGenAc: Boolean = false,
        val relayResistanceThresholdExceeded: Boolean = false,
        var relayResistanceTimeLimitsExceeded: Boolean = false,
        var relayResistancePerformed: RelayResistancePerformed = RelayResistancePerformed.RRP_NOT_SUPPORTED,
    ) : IByteable {

        override fun toByteArray(): ByteArray {
            val b1 = listOf(
                offlineDataAuthenticationWasNotPerformed,
                sdaFailed,
                iccDataMissing,
                cardAppearsOnTerminalExceptionFile,
                ddaFailed,
                cdaFailed
            )
            val b2 = listOf(
                iccAndTerminalHaveDifferentApplicationVersions,
                expiredApplication,
                applicationNotYetEffective,
                requestedServiceNotAllowedForCardProduct,
                newCard,
            )
            val b3 = listOf(
                cardholderVerificationWasNotSuccessful,
                unrecognizedCvm,
                pinTryLimitExceeded,
                pinEntryRequiredAndPinpadNotPresentOrNotWorking,
                pinEntryRequiredPinpadPresentButPinNotEntered,
                onlinePinEntered,
            )
            val b4 = listOf(
                transactionExceedsFloorLimit,
                lowerConsecutiveOfflineLimitExceeded,
                upperConsecutiveOfflineLimitExceeded,
                transactionSelectedRandomlyForOnlineProcessing,
                merchantForcedTransactionOnline,
            )
            val b5 = listOf(
                defaultTdolUsed,
                issuerAuthenticationFailed,
                scriptProcessingFailedBeforeFinalGenAc,
                scriptProcessingFailedAfterFinalGenAc,
                relayResistanceThresholdExceeded,
                relayResistanceTimeLimitsExceeded
            )
            var tempB5 = getByteFromList(b5)
            tempB5 = when (relayResistancePerformed) {
                RelayResistancePerformed.RRP_NOT_SUPPORTED -> tempB5
                RelayResistancePerformed.RRP_NOT_PERFORMED -> tempB5.or(0b01)
                RelayResistancePerformed.RRP_PERFORMED -> tempB5.or(0b10)
                RelayResistancePerformed.RFU -> tempB5.or(0b11)
            }

            return byteArrayOf(
                getByteFromList(b1),
                getByteFromList(b2),
                getByteFromList(b3),
                getByteFromList(b4),
                tempB5
            )
        }

        private fun setBitOfByte(offset: Int, byte: Byte, should: Boolean): Byte {
            return if (should) {
                byte.or((1 shl offset).toByte())
            } else {
                byte
            }
        }

        private fun getByteFromList(list: List<Boolean>): Byte {
            var b: Byte = 0
            var offset = 7
            for (check in list) {
                b = setBitOfByte(offset--, b, check)
            }
            return b
        }

        enum class RelayResistancePerformed(val value: Byte) {
            RRP_NOT_SUPPORTED(0X00),
            RRP_NOT_PERFORMED(0B01),
            RRP_PERFORMED(0B10),
            RFU(0B11);

            companion object {
                fun from(findValue: Byte): RelayResistancePerformed =
                    values().first { it.value == findValue }
            }
        }
    }

}
