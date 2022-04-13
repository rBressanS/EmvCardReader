package stone.ton.tapreader.classes.pos.readercomponents

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import com.payneteasy.tlv.BerTlvParser
import stone.ton.tapreader.ReadActivity
import stone.ton.tapreader.classes.apdu.APDUCommand
import stone.ton.tapreader.classes.apdu.APDUResponse
import stone.ton.tapreader.classes.dataclasses.TerminalTag
import stone.ton.tapreader.classes.emv.AFLEntry
import stone.ton.tapreader.classes.emv.CandidateApp
import stone.ton.tapreader.classes.emv.DOL
import stone.ton.tapreader.classes.utils.General.Companion.isPureAscii
import java.io.IOException

class EmvReaderCallback(
    var terminalCandidateList: ArrayList<String>,
    var readActivity: ReadActivity,
    var terminalTags: List<TerminalTag>,
) : NfcAdapter.ReaderCallback {
    private val parser = BerTlvParser()
    private lateinit var tagcomm: IsoDep

    fun pickApplicationFromCandidateList(
        terminalList: List<String>,
        cardList: List<CandidateApp>,
    ): CandidateApp? {
        var chosenApp: CandidateApp? = null
        for (cardApp in cardList) {
            if (terminalList.contains(cardApp.aid.toHex().replace(" ", ""))) {
                if (chosenApp == null || cardApp.priority[0] < chosenApp.priority[0]) {
                    chosenApp = cardApp
                }
            }
        }
        return chosenApp
    }

    override fun onTagDiscovered(it: Tag?) {
        tagcomm = IsoDep.get(it)
        try {
            tagcomm.connect()
        } catch (e: IOException) {
            println(e)
            Log.i("EMVemulator", "Error tagcomm: " + e.message)
            return
        }
        try {
            val getPPSEApdu =
                APDUCommand.getForSelectApplication("32 50 41 59 2E 53 59 53 2E 44 44 46 30 31".decodeHex())
            val ppseResponse = transceive(getPPSEApdu)
            val cardCandidateList = parsePPSEResponse(ppseResponse)
            val selectableApp =
                pickApplicationFromCandidateList(terminalCandidateList, cardCandidateList)
                    ?: return
            val getAppData = APDUCommand.getForSelectApplication(selectableApp.aid)
            val appResponse = transceive(getAppData)
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
            tagcomm.close()
        } catch (e: IOException) {
            Log.i("EMVemulator", "Error tranceive: " + e.message)
        }
    }


    fun buildPDOLValue(tag9F38: BerTlv?): ByteArray {
        val fullPdol: ByteArray
        var pdolValue = ByteArray(0)
        if (tag9F38 != null) {
            val PDOL = DOL(tag9F38.bytesValue)
            for (entry in PDOL.requiredTags) {
                val terminalTag = terminalTags.find { it.tag == entry.key }
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

    fun parsePPSEResponse(receive: APDUResponse): List<CandidateApp> {
        val fciTemplate = parser.parseConstructed(receive.data)
        Log.i("TESTE", fciTemplate.toString())
        val a5 = fciTemplate.find(BerTag(0xA5))
        val bf0c = a5.find(BerTag(0xbf, 0x0c))
        val appTemplateList = bf0c.findAll(BerTag(0x61))
        val candidateList: ArrayList<CandidateApp> = ArrayList()
        for (appTemplate in appTemplateList) {
            val candidateApp = CandidateApp(appTemplate)
            Log.i("EMV", "Application: " + candidateApp.aid.toHex())
            candidateList.add(candidateApp)
        }
        return candidateList
    }

    private fun String.decodeHex(): ByteArray {
        check((this.replace(" ", "").length % 2) == 0) { "Must have an even length" }

        return replace(" ", "").chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArray.toHex(): String {
        /*!
        This method converts bytes to strings of hex
        */
        val result = StringBuilder()
        for (inputbyte in this) {
            result.append(String.format("%02X" + " ", inputbyte))
        }
        return result.toString().trim()
    }

    private fun transceive(command: APDUCommand): APDUResponse {
        return transceive(command.getAsBytes().toHex())
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
        Log.i("EMVemulator", "Send: " + bytes.toHex())
        readActivity.addToApduTrace("Send", bytes.toHex())
        val recv = tagcomm.transceive(bytes)
        Log.i("EMVemulator", "Received: " + recv.toHex())
        readActivity.addToApduTrace("Receive", recv.toHex())
        val response = APDUResponse(fullData = recv)
        if (response.wasSuccessful()) {
            dumpBerTlv(parser.parseConstructed(response.data))
        }
        readActivity.addToApduTrace("", "-".repeat(75))
        return response
    }


    fun dumpBerTlv(tlv: BerTlv, level: Int = 0) {
        val lvlDump = " ".repeat(level * 4)
        if (tlv.isPrimitive) {
            val isAscii: Boolean = isPureAscii(tlv.bytesValue)
            if (isAscii) {
                readActivity.addToApduTrace(
                    "TLV",
                    lvlDump + tlv.tag.toString() + " - " + tlv.hexValue + " (" + tlv.textValue + ")"
                )
            } else {
                readActivity.addToApduTrace("TLV",
                    lvlDump + tlv.tag.toString() + " - " + tlv.hexValue)
            }
        } else {
            readActivity.addToApduTrace("TLV", lvlDump + tlv.tag.toString())
            for (tag in tlv.values) {
                dumpBerTlv(tag, level + 1)
            }
        }
    }
}