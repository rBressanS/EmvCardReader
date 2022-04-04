package stone.ton.tapreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import com.payneteasy.tlv.BerTlvParser
import stone.ton.tapreader.classes.apdu.APDUCommand
import stone.ton.tapreader.classes.apdu.APDUResponse
import stone.ton.tapreader.classes.apdu.TerminalDataBuilder
import stone.ton.tapreader.classes.emv.AFLEntry
import stone.ton.tapreader.classes.emv.CandidateApp
import stone.ton.tapreader.classes.emv.DOL
import java.io.IOException


class ReadActivity : AppCompatActivity() {
    private lateinit var nfcAdapter: NfcAdapter
    private var tag /*!< represents an NFC tag that has been discovered */: Tag? = null
    private var tagcomm /*!< provides access to ISO-DEP (ISO 14443-4) properties and I/O operations on a Tag */: IsoDep? =
        null
    private var techListsArray = arrayOf(arrayOf<String>(IsoDep::class.java.name))
    private lateinit var intentFiltersArray: Array<IntentFilter>
    private var nfcintent /*!< reference to a token maintained by the system describing the original data used to retrieve it */: PendingIntent? =
        null
    private var apduTrace: TextView? = null

    private val parser = BerTlvParser()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read)

        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        apduTrace = findViewById(R.id.apduTrace)
        apduTrace!!.movementMethod = ScrollingMovementMethod()
        nfcintent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        intentFiltersArray = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val extra = Bundle()
        extra.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1)
        nfcAdapter.enableReaderMode(
            this, NfcAdapter.ReaderCallback
            {
                Log.i("Reader Mode", it.toString())
                Log.i("Reader Mode", it.techList.toString())
                addToApduTrace("Reader Mode", it.toString())
                addToApduTrace("Reader Tech", it.techList.toString())
                tagcomm = IsoDep.get(it)
                try {
                    tagcomm?.connect()
                    addToApduTrace("Connected", "OK")
                } catch (e: IOException) {
                    Log.i("EMVemulator", "Error tagcomm: " + e.message)
                    addToApduTrace("ERROR", e.message.toString())
                    return@ReaderCallback
                }
                try {
                    val getPPSEApdu =
                        APDUCommand.getForSelectApplication("32 50 41 59 2E 53 59 53 2E 44 44 46 30 31".decodeHex())
                    val ppseResponse = transceive(getPPSEApdu)
                    val candidateList = parsePPSEResponse(ppseResponse)
                    val getAppData = APDUCommand.getForSelectApplication(candidateList[1].aid)
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
                            val readRecord = APDUCommand.getForReadRecord(sfiP2, record.toByte())
                            transceive(readRecord)
                        }

                    }
                    tagcomm?.close()
                } catch (e: IOException) {
                    Log.i("EMVemulator", "Error tranceive: " + e.message)
                    addToApduTrace("ERROR", e.message.toString())
                }
            },
            (NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK + NfcAdapter.FLAG_READER_NFC_A + NfcAdapter.FLAG_READER_NFC_B),
            extra
        )

    }

    fun buildPDOLValue(tag9F38: BerTlv?): ByteArray {
        val fullPdol: ByteArray
        var pdolValue = ByteArray(0)
        if (tag9F38 != null) {
            val PDOL = DOL(tag9F38.bytesValue)
            val terminalData = TerminalDataBuilder()
            for (entry in PDOL.requiredTags) {
                if (terminalData.terminalTags.containsKey(entry.key) && terminalData.terminalTags[entry.key]!!.size == entry.value) {
                    pdolValue += terminalData.terminalTags[entry.key]!!
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
            Log.i("EMV", "Application: " + byte2Hex(candidateApp.label))
            //0x87 = Application Priority Indicator
            addToApduTrace(
                "PPSE",
                candidateApp.priority.toString() + "-" + byte2Hex(candidateApp.aid) + "-" + candidateApp.label
            )
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

    fun clearTrace(@SuppressWarnings("UnusedParameters") view: View) {
        apduTrace!!.text = ""
    }

    private fun addToApduTrace(tag:String, chars: CharSequence){
        apduTrace!!.append("$tag: $chars\n")
    }

    fun String.decodeHex(): ByteArray {
        check((this.replace(" ", "").length % 2) == 0) { "Must have an even length" }

        return replace(" ", "").chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    public override fun onResume() {
        /*!
            This method is called when user returns to the activity
         */
        super.onResume()
        nfcAdapter.enableForegroundDispatch(this, nfcintent, intentFiltersArray, techListsArray)
    }


    public override fun onPause() {
        /*!
            This method disable reader mode (enable emulation) when user leave the activity
         */
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
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
        addToApduTrace("Send", byte2Hex(bytes))
        val recv = tagcomm!!.transceive(bytes)
        Log.i("EMVemulator", "Received: " + byte2Hex(recv))
        addToApduTrace("Receive", byte2Hex(recv))
        val response = APDUResponse(fullData = recv)
        if (response.wasSuccessful()) {
            dumpBerTlv(parser.parseConstructed(response.data))
        }
        return response
    }

}