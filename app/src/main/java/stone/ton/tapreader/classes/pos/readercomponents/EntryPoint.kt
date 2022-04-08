package stone.ton.tapreader.classes.pos.readercomponents

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import com.payneteasy.tlv.BerTlvParser
import stone.ton.tapreader.ReadActivity
import stone.ton.tapreader.classes.apdu.APDUCommand
import stone.ton.tapreader.classes.apdu.APDUResponse
import stone.ton.tapreader.classes.dataclasses.TerminalTag
import stone.ton.tapreader.classes.dataclasses.kernel.KernelData
import stone.ton.tapreader.classes.emv.AFLEntry
import stone.ton.tapreader.classes.emv.CandidateApp
import stone.ton.tapreader.classes.emv.DOL
import java.io.IOException


class EntryPoint(
    var readActivity: ReadActivity,
    var kernels: List<KernelData>,
    var terminalTags: List<TerminalTag>,
) {

    private val parser = BerTlvParser()
    private var tagcomm: IsoDep? = null

    fun pickApplicationFromCandidateList(terminalList: List<String>, cardList: List<CandidateApp>): CandidateApp? {
        var chosenApp: CandidateApp? = null
        for (cardApp in cardList){
            if(terminalList.contains(byte2Hex(cardApp.aid).replace(" ",""))){
                if(chosenApp == null || cardApp.priority[0] < chosenApp.priority[0]){
                    chosenApp = cardApp
                }
            }
        }
        return chosenApp
    }

    fun readCardData() {
        val terminalCandidateList = buildCandidateList(10, "credit")
        val intent = Intent(readActivity, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val nfcintent =
            PendingIntent.getActivity(readActivity, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val intentFiltersArray = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))

        val nfcAdapter = NfcAdapter.getDefaultAdapter(readActivity)
        val extra = Bundle()
        extra.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000)
        readActivity.onResumeCallback = {
            nfcAdapter.enableForegroundDispatch(
                readActivity,
                nfcintent,
                intentFiltersArray,
                arrayOf(arrayOf<String>(IsoDep::class.java.name))
            )

            nfcAdapter.enableReaderMode(
                readActivity, NfcAdapter.ReaderCallback
                {
                    Log.i("Reader Mode", it.toString())
                    Log.i("Reader Mode", it.techList.toString())
                    tagcomm = IsoDep.get(it)
                    try {
                        tagcomm?.connect()
                    } catch (e: IOException) {
                        println(e)
                        Log.i("EMVemulator", "Error tagcomm: " + e.message)
                        return@ReaderCallback
                    }
                    try {
                        val getPPSEApdu =
                            APDUCommand.getForSelectApplication("32 50 41 59 2E 53 59 53 2E 44 44 46 30 31".decodeHex())
                        val ppseResponse = transceive(getPPSEApdu)
                        val cardCandidateList = parsePPSEResponse(ppseResponse)
                        val selectableApp = pickApplicationFromCandidateList(terminalCandidateList, cardCandidateList)
                            ?: return@ReaderCallback
                        val getAppData = APDUCommand.getForSelectApplication(selectableApp.aid)
                        var appResponse = transceive(getAppData)
                        val fullPdol = buildPDOLValue(
                            parser.parseConstructed(appResponse.data).find(BerTag(0xA5))
                                .find(BerTag(0x9F, 0x38))
                        )
                        val gpoRequest =
                            APDUCommand.getForGetProcessingOptions(fullPdol)
                        val gpoResponse = transceive(gpoRequest)
                        val responseTemplate = parser.parseConstructed(gpoResponse.data)
                        Log.i("TESTE", responseTemplate.toString())
                        val aflValue = responseTemplate.find(BerTag(0x94))
                        val aflEntries = aflValue.bytesValue.toList().chunked(4)
                        for (data in aflEntries) {
                            val aflEntry = AFLEntry(data.toByteArray())
                            for (record in aflEntry.start..aflEntry.end) {
                                val sfiP2 = aflEntry.getSfiForP2()
                                val readRecord =
                                    APDUCommand.getForReadRecord(sfiP2, record.toByte())
                                transceive(readRecord)
                            }

                        }
                        tagcomm?.close()
                    } catch (e: IOException) {
                        Log.i("EMVemulator", "Error tranceive: " + e.message)
                    }
                },
                (NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK + NfcAdapter.FLAG_READER_NFC_A + NfcAdapter.FLAG_READER_NFC_B),
                extra
            )
        }
        readActivity.onPauseCallback = {
            nfcAdapter.disableForegroundDispatch(readActivity)
            nfcAdapter.disableReaderMode(readActivity)
        }

    }

    fun buildPDOLValue(tag9F38: BerTlv?): ByteArray {
        val fullPdol: ByteArray
        var pdolValue = ByteArray(0)
        if (tag9F38 != null) {
            val PDOL = DOL(tag9F38.bytesValue)
            for (entry in PDOL.requiredTags) {
                val terminalTag = terminalTags.find { it.tag == entry.key }
                if (terminalTag != null && terminalTag.valueAsByteArray.size == entry.value) {
                    pdolValue += terminalTag.valueAsByteArray
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

    fun parsePPSEResponse(receive: APDUResponse): List<CandidateApp> {
        val fciTemplate = parser.parseConstructed(receive.data)
        Log.i("TESTE", fciTemplate.toString())
        val a5 = fciTemplate.find(BerTag(0xA5))
        val bf0c = a5.find(BerTag(0xbf, 0x0c))
        val appTemplateList = bf0c.findAll(BerTag(0x61))
        val candidateList: ArrayList<CandidateApp> = ArrayList()
        for (appTemplate in appTemplateList) {
            val candidateApp = CandidateApp(appTemplate)
            //0x4F = ApplicationID
            Log.i("EMV", "Application: " + byte2Hex(candidateApp.aid))
            //0x50 = Application Label
            //Log.i("EMV", "Application: " + byte2Hex(candidateApp.label))
            //0x87 = Application Priority Indicator
            candidateList.add(candidateApp)
        }
        return candidateList
    }

    private fun isPureAscii(s: ByteArray?): Boolean {
        var result = true
        if (s != null) {
            for (i in s.indices) {
                val c = s[i].toInt()
                if (c < 31 || c > 127) {
                    result = false
                    break
                }
            }
        }
        return result
    }

    fun dumpBerTlv(tlv: BerTlv, level: Int = 0) {
        val lvlDump = " ".repeat(level)
        if (tlv.isPrimitive) {
            val isAscii: Boolean = isPureAscii(tlv.bytesValue)
            if (isAscii) {
                Log.i(
                    "TLV",
                    lvlDump + tlv.tag.toString() + " - " + tlv.hexValue + " (" + tlv.textValue + ")"
                )
            } else {
                Log.i("TLV", lvlDump + tlv.tag.toString() + " - " + tlv.hexValue)
            }
        } else {
            Log.i("TLV", lvlDump + tlv.tag.toString())
            for (tag in tlv.values) {
                dumpBerTlv(tag, level + 1)
            }
        }
    }

    fun String.decodeHex(): ByteArray {
        check((this.replace(" ", "").length % 2) == 0) { "Must have an even length" }

        return replace(" ", "").chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun byte2Hex(input: ByteArray): String {
        /*!
        This method converts bytes to strings of hex
        */
        val result = StringBuilder()
        for (inputbyte in input) {
            result.append(String.format("%02X" + " ", inputbyte))
        }
        return result.toString().trim()
    }

    private fun transceive(command: APDUCommand): APDUResponse {
        return transceive(byte2Hex(command.getAsBytes()))
    }

    private fun transceive(hexstr: String): APDUResponse {
        /*!
            This method transceives all the data.
         */
        val hexbytes: Array<String> = hexstr.split(" ").toTypedArray()
        val bytes = ByteArray(hexbytes.size)
        for (i in hexbytes.indices) {
            bytes[i] = hexbytes[i].toInt(16).toByte()
        }
        Log.i("EMVemulator", "Send: " + byte2Hex(bytes))
        readActivity.addToApduTrace("Send", byte2Hex(bytes))
        val recv = tagcomm!!.transceive(bytes)
        Log.i("EMVemulator", "Received: " + byte2Hex(recv))
        readActivity.addToApduTrace("Receive", byte2Hex(recv))
        val response = APDUResponse(fullData = recv)
        if (response.wasSuccessful()) {
            dumpBerTlv(parser.parseConstructed(response.data))
        }
        return response
    }

    private fun buildCandidateList(
        amount: Int,
        additionalFilter: String = "",
        transactionType: String? = "00",
    ): ArrayList<String> {
        val candidateList = ArrayList<String>()
        for (kernel in kernels) {
            for (kernelApp in kernel.kernelApplications) {
                if (additionalFilter != "" && kernelApp.filteringSelector != additionalFilter) {
                    continue
                }
                for (transactionConfig in kernelApp.transactionConfig) {
                    if (transactionConfig.transactionType == transactionType) {
                        if (!transactionConfig.zeroAmountAllowedFlag && amount == 0) {
                            continue
                        }
                        if (transactionConfig.readerContactlessTransactionLimit != null) {
                            if (amount >= transactionConfig.readerContactlessTransactionLimit!!) {
                                continue
                            }
                        }
                    }
                }
                candidateList.addAll(kernelApp.aids)
            }
        }
        return candidateList
    }


    val readerCombinations: ArrayList<EntryPointCombination> = arrayListOf(
        EntryPointCombination("A0000000041010", 4),
        EntryPointCombination("A0000000043060", 4),
        EntryPointCombination("A0000000031010", 2),
        EntryPointCombination("A0000000032010", 2),
    )

    fun buildPreProcessIndicators(amountAuthorized: Int) {
        for (combinationEntry in readerCombinations) {

            val combinationData = combinationEntry.configurationData
            val combinationIndicators = combinationEntry.preProcessingIndicators

            combinationIndicators.isStatusCheckRequested =
                combinationData.isStatusCheckSupport && amountAuthorized > -1

            if (amountAuthorized == 0) {
                if (!combinationData.isZeroAmountAllowedForOffline) {
                    if (!combinationData.isZeroAmountAllowed) {
                        combinationIndicators.isContactlessApplicationNotAllowed = true
                    } else {
                        combinationIndicators.isZeroAmount = true
                    }
                }
            }
            if (amountAuthorized >= combinationData.readerContactlessTransactionLimit) {
                combinationIndicators.isContactlessApplicationNotAllowed = true
            }

            if (amountAuthorized >= combinationData.readerContactlessFloorLimit) {
                combinationIndicators.isReaderContactlessFloorLimitExceeded = true
            }

            if (amountAuthorized >= combinationData.readerCvmRequiredLimit) {
                combinationIndicators.isReaderCvmRequiredLimitExceeded = true
            }

            if (combinationData.TTQ.isNotEmpty()) {
                combinationIndicators.TTQ = combinationData.TTQ
                combinationIndicators.TTQ =
                    setValueForByteAndBit(combinationIndicators.TTQ, 1, 7, false)
                combinationIndicators.TTQ =
                    setValueForByteAndBit(combinationIndicators.TTQ, 1, 8, false)

                if (combinationIndicators.isReaderContactlessFloorLimitExceeded || combinationIndicators.isStatusCheckRequested) {
                    combinationIndicators.TTQ =
                        setValueForByteAndBit(combinationIndicators.TTQ, 1, 8, true)
                }

                if (combinationIndicators.isZeroAmount) {
                    if ((combinationIndicators.TTQ[0].toInt() and 0b1000) == 0) {
                        combinationIndicators.TTQ =
                            setValueForByteAndBit(combinationIndicators.TTQ, 1, 8, true)
                    } else {
                        combinationIndicators.isContactlessApplicationNotAllowed = true
                    }
                }

                if (combinationIndicators.isReaderCvmRequiredLimitExceeded) {
                    combinationIndicators.TTQ =
                        setValueForByteAndBit(combinationIndicators.TTQ, 1, 7, true)
                }


            }

        }

    }

    fun preProcess(amountAuthorized: Int): Outcome {
        buildPreProcessIndicators(amountAuthorized)
        val hasAnyContactlessAllowed =
            readerCombinations.any { !it.preProcessingIndicators.isContactlessApplicationNotAllowed }
        if (!hasAnyContactlessAllowed) {
            return Outcome.TRY_ANOTHER_INTERFACE
        }
        return activateProtocol()
    }

    fun activateProtocol(): Outcome {
        return selectCombination()
    }

    fun selectCombination(): Outcome {
        return activateKernel()
    }

    fun activateKernel(): Outcome {
        return processByKernel()
    }

    fun processByKernel(): Outcome {
        return Outcome.APPROVED
    }

    fun runForStartA(amountAuthorized: Int) {
        preProcess(amountAuthorized)
    }

    fun runForStartB() {
        activateProtocol()
    }

    fun runForStartC() {
        selectCombination()
    }

    fun runForStartD() {
        activateKernel()
    }

    fun setValueForByteAndBit(
        byteArray: ByteArray,
        byteNum: Int,
        bitNum: Int,
        value: Boolean,
    ): ByteArray {
        var myByteArray = byteArray.clone()
        if (value) {
            myByteArray[byteNum] = (myByteArray[byteNum].toInt() or (1 shl bitNum)).toByte()
        } else {
            myByteArray[byteNum] = (myByteArray[byteNum].toInt() and (0 shl bitNum)).toByte()
        }

        return myByteArray
    }

}

class EntryPointCombination(val AID: String, val kernelId: Int) {
    constructor(
        AID: String,
        kernelId: Int,
        configurationData: EPConfigurationData,
        preProcessingIndicators: EPPreProcessingIndicators,
    ) : this(AID, kernelId) {
        this.configurationData = configurationData
        this.preProcessingIndicators = preProcessingIndicators
    }

    var configurationData: EPConfigurationData = EPConfigurationData()
    var preProcessingIndicators: EPPreProcessingIndicators = EPPreProcessingIndicators()
}

class EPConfigurationData {
    val isStatusCheckSupport = false
    val isZeroAmountAllowed = false
    val isZeroAmountAllowedForOffline = false
    val readerContactlessTransactionLimit = 0
    val readerContactlessFloorLimit = 0
    val terminalFloorLimit = 0
    val readerCvmRequiredLimit = 0
    val TTQ = byteArrayOf(0x25, 0, 0x40, 0) //TerminalTransactionQualifier
    val extendedSelectionSupport = true
}

class EPPreProcessingIndicators {
    var isStatusCheckRequested = false
    var isContactlessApplicationNotAllowed = false
    var isZeroAmount = false
    val isZeroAmountAllowed = false
    var isReaderCvmRequiredLimitExceeded = false
    var isReaderContactlessFloorLimitExceeded = false
    var TTQ = ByteArray(0) //TerminalTransactionQualifier
}
