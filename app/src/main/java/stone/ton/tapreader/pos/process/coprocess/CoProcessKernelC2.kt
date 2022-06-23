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
import stone.ton.tapreader.utils.DataSets
import stone.ton.tapreader.utils.General.Companion.decodeHex
import stone.ton.tapreader.utils.General.Companion.getIntValue
import stone.ton.tapreader.utils.General.Companion.isBitSet
import stone.ton.tapreader.utils.General.Companion.isBitSetOfByte
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
        "FF8101"
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

    private fun buildOutSignal(): OutSignal {
        kernelDatabase.add(
            BerTlvBuilder().addBytes(BerTag("DF8115".decodeHex()), errorIndication).buildTlv()
        )
        initializeDiscretionaryData()
        return OutSignal(outcomeParameter, null, DataRecord(), discretionaryData)
    }

    private fun addTagToDatabase(tag: String, value: String) {
        kernelDatabase.add(
            BerTlvBuilder().addHex(BerTag(tag.decodeHex()), value).buildTlv()
        )
    }

    private fun isBitSet(n: Byte, k: Byte): Boolean {
        return (n and ((1 shl k - 1).toByte())) != 0.toByte()
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
        return if (isBitSet(aip.bytesValue[0], 2)) {
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
                            tvr.setBitOfByte(1, 4, false)
                            tvr.setBitOfByte(2, 4, true)
                            println("RRP OK")
                            null
                        }
                    }
                    if (exceededMaximum) {
                        tvr.setBitOfByte(3, 4)
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
                        tvr.setBitOfByte(1, 4, false)
                        tvr.setBitOfByte(2, 4, true)
                        println("RRP OK")
                    } else {
                        //31
                        tvr.setBitOfByte(4, 4)
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
        addTagToDatabase("9F37", "00000000")
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
        generateUnpredictableNumber()

        var outSignal = processFciResponse(startProcessPayload.fciResponse)
        if (outSignal != null) {
            return outSignal
        }

        val aip = kernelDatabase.getTlv(byteArrayOf(0x82.toByte()))!!.fullTag

        if (!isBitSet(aip.bytesValue[1], 8)) {
            errorIndication[1] = 0x07
            return processErrorInGpo()
        }

        val aflValue = kernelDatabase.getTlv(byteArrayOf(0x94.toByte()))!!.fullTag
        val activeAfl = aflValue.bytesValue
        val readerContactlessTransactionLimit = getCtlessTrxLimit(aip)

        println(aip.hexValue)

        if (isBitSet(aip.bytesValue[1], 1)) {
            //Supports RRP
            val rrpEntropy = kernelDatabase.getTlv("9F37".decodeHex())!!.fullTag.bytesValue
            outSignal = processRRP(rrpEntropy)
            if (outSignal != null) {
                return outSignal
            }
        } else {
            //Does not supports RRP
            tvr.setBitOfByte(1, 4)
            tvr.setBitOfByte(2, 4, false)
        }

        if (isBitSet(aip.bytesValue[0], 1) && isBitSet(terminalCapabilities.byte3.toByte(), 4)) {
            odaStatus = "CDA"
        } else {
            tvr.setBitOfByte(8, 0) // Offline data authentication was not performed
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
                if (aflEntry.sfi <= 10) {
                    if (readRecordResponseData.bytesValue.isNotEmpty() && readRecordResponseData.tag == BerTag(
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
                cdol = kernelDatabase.getTlv("80".decodeHex())?.fullTag
                if (aflEntry.isSigned && odaStatus == "CDA") {
                    if (aflEntry.sfi <= 10) {
                        if (signedData.length < 2048 * 2) {
                            signedData += readRecordResponseData.getListAsByteArray().toHex()
                        } else {
                            tvr.setBitOfByte(3, 0) // CDA Failed
                        }
                    } else {
                        if (readRecordResponseData.getListAsByteArray()
                                .isNotEmpty() && readRecordResponseData.tag == BerTag(0x70) && signedData.length < 2048 * 2
                        ) {
                            signedData += "70" + readRecordResponseData.getFullAsByteArray()
                        } else {
                            tvr.setBitOfByte(3, 0) // CDA Failed
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
            tvr.setBitOfByte(6, 0) // ICC data missing
            tvr.setBitOfByte(7, 0) // CDA Failed
        }
        val caPkIndex = kernelDatabase.getTlv(BerTag(0x8F))!!.fullTag.hexValue!!
        val rid = "A000000004"
        val caPk = DataSets.caPublicKeys.find { it.index == caPkIndex && it.rId == rid }
        if (caPk == null) {
            tvr.setBitOfByte(7, 0) // CDA Failed
        }
        val sdaTag = kernelDatabase.getTlv(BerTag(0x9F, 0x4A))
        if (!(sdaTag != null && sdaTag.fullTag.intValue == 0x82)) {
            return buildOutSignal() // TODO corrigir 257 456.27.1
        }
        if (signedData.length < 2048 * 2) {
            signedData += sdaTag.fullTag.getFullAsByteArray().toHex()
        } else {
            tvr.setBitOfByte(7, 0) // CDA Failed
        }
        val readerCvmRequired = kernelDatabase.getTlv("DF8126".decodeHex())!!.fullTag.intValue
        if (amountAuthorized > readerCvmRequired) {
            //31
            outcomeParameter.receipt = true
            terminalCapabilities.byte2 =
                kernelDatabase.getTlv("DF8118".decodeHex())!!.fullTag.intValue
        } else {
            //33
            terminalCapabilities.byte2 =
                kernelDatabase.getTlv("DF8119".decodeHex())!!.fullTag.intValue
        }

        //TODO pre-generate AC balance reading

        processingRestrictions()

        //todo cvm selection
        cvmSelection()

        val readerContactlessFloorLimit =
            kernelDatabase.getTlv("DF8123".decodeHex())!!.fullTag.intValue
        if (amountAuthorized > readerContactlessFloorLimit) {
            tvr.setBitOfByte(8, 3) // trx exceeds floor limit
        }

        //TODO Terminal Action Analysis

        //TODO Generate AC

        //Start S12


        return null
    }

    private fun cvmSelection() {
        val aip = kernelDatabase.getTlv(byteArrayOf(0x82.toByte()))!!.fullTag.bytesValue
        val supportsCDCVM = aip.isBitSetOfByte(2, 0)
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
        if (!aip.isBitSetOfByte(5, 0)) {
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
            tvr.setBitOfByte(6, 0)
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
            val cvRuleShouldContinue = cvCode.isBitSet(7)
            if (cvCondition != null) {
                //TODO implement should Apply
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
                        tvr.setBitOfByte(7, 2) // Unrecognized CVM
                        if (cvRuleShouldContinue) {
                            continue
                        }
                        outcomeParameter.CVM = 0x00 // No Cvm
                        tvr.setBitOfByte(8, 2) // Cardholder verification was not successful
                        if (cvmCode == CvmCode.FAIL_CVM) {
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
                }

            }
        }
        cvmResult[0] = 0x3F
        cvmResult[1] = 0x00
        cvmResult[2] = 0x01
        outcomeParameter.CVM = 0x00 // No CVM
        tvr.setBitOfByte(8, 2) // Cardholder verification was not successful
        return
    }

    private fun shouldApplyCondition(
        condition: CvmCondition,
        xValue: Int,
        yValue: Int,
        amount: Int,
        transactionType: String?
    ): Boolean {
        //TODO implement
        return true
    }

    enum class CvmCode(val value: Int) {
        FAIL_CVM(0x00),
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
                tvr.setBitOfByte(8, 1) // ICC and terminal have different application versions
            }
        }
    }

    private fun validateExpirationDate() {
        val appEffectiveDate = kernelDatabase.getTlv("5F25".decodeHex())?.fullTag?.hexValue
        val appExpirationDate = kernelDatabase.getTlv("5F24".decodeHex())!!.fullTag.hexValue
        val transactionDate = kernelDatabase.getTlv("9A".decodeHex())!!.fullTag.hexValue
        if (appEffectiveDate != null && appEffectiveDate > transactionDate) {
            tvr.setBitOfByte(6, 1) // App not yet effective
        }
        if (transactionDate > appExpirationDate) {
            tvr.setBitOfByte(7, 1) // App expired
        }
    }

    private fun validateAppUsageControl() {
        val appUsageControl =
            kernelDatabase.getTlv("9F07".decodeHex())?.fullTag?.bytesValue ?: return
        val terminalType = kernelDatabase.getTlv("9F35".decodeHex())!!.fullTag.hexValue
        val additionalTerminalCapabilities =
            kernelDatabase.getTlv("9F40".decodeHex())!!.fullTag.bytesValue

        if ((terminalType == "14" || terminalType == "15" || terminalType == "16") && (additionalTerminalCapabilities.isBitSetOfByte(
                8,
                0
            ))
        ) {
            if (!appUsageControl.isBitSetOfByte(2, 1)) {
                tvr.setBitOfByte(5, 1) // Requested service not allowed for card product
                return
            }
        } else {
            if (!appUsageControl.isBitSetOfByte(1, 0)) {
                tvr.setBitOfByte(5, 1) // Requested service not allowed for card product
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
                if (!appUsageControl.isBitSetOfByte(8, 0)) {
                    tvr.setBitOfByte(5, 1) // Requested service not allowed for card product
                }
            } else {
                if (!appUsageControl.isBitSetOfByte(7, 0)) {
                    tvr.setBitOfByte(5, 1) // Requested service not allowed for card product
                }
            }
        }

        val isPurchaseTransaction = transactionType == "00" || transactionType == "09"

        if (isPurchaseTransaction) {
            if (isSameCountry) {
                if (!(appUsageControl.isBitSetOfByte(6, 0) || appUsageControl.isBitSetOfByte(
                        4,
                        0
                    ))
                ) {
                    tvr.setBitOfByte(5, 1) // Requested service not allowed for card product
                }
            } else {
                if (!(appUsageControl.isBitSetOfByte(5, 0) || appUsageControl.isBitSetOfByte(
                        3,
                        0
                    ))
                ) {
                    tvr.setBitOfByte(5, 1) // Requested service not allowed for card product
                }
            }
        }

        val cashbackAmount = kernelDatabase.getTlv("9F03".decodeHex())?.fullTag?.intValue
        if (cashbackAmount != null && cashbackAmount > 0) {
            if (isSameCountry) {
                if (!appUsageControl.isBitSetOfByte(8, 1)) {
                    tvr.setBitOfByte(5, 1) // Requested service not allowed for card product
                }
            } else {
                if (!appUsageControl.isBitSetOfByte(7, 1)) {
                    tvr.setBitOfByte(5, 1) // Requested service not allowed for card product
                }
            }
        }

    }

    private fun processingRestrictions() {
        validateAppVersionNumber()
        validateExpirationDate()
        validateAppUsageControl()
    }

    private fun hasMandatoryCdaTags(): Boolean {
        return (kernelDatabase.isPresent(byteArrayOf(0x8F.toByte())) // CA Public Key Index
                && kernelDatabase.isPresent(byteArrayOf(0x90.toByte())) // Issuer Public Key Certificate
                && kernelDatabase.isPresent(byteArrayOf(0x9F32.toByte())) // Issuer Public Key Exponent
                && kernelDatabase.isPresent(byteArrayOf(0x9F46.toByte())) // ICC Public Key Certificate
                && kernelDatabase.isPresent(byteArrayOf(0x9F47.toByte())) // ICC Public Key Exponent
                && kernelDatabase.isPresent(byteArrayOf(0x9F4A.toByte())) // Static data authentication tag list
                )
    }

    private fun buildDolValue(requestedDol: BerTlv?): ByteArray {
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

    data class OutcomeParameter(
        var status: Int = 0,
        var start: Int = 0,
        val onlineResponseData: Int = 0,
        var CVM: Int = 0,
        var uiRequestOnOutcomePresent: Boolean = false,
        val uiRequestOnRestartPresent: Boolean = false,
        val dataRecordPresent: Boolean = false,
        val didescretionaryDataPresent: Boolean = false,
        var receipt: Boolean = false,
        val alternateInterfacePreference: Int = 0,
        var fieldOffRequest: Int = 0,
        val removalTimeount: Int = 0,
    )

    class DataRecord

    class DiscretionaryData(var data: MutableList<BerTlv>) {
        fun add(tag: BerTlv) {
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