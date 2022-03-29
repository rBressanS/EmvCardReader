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
import stone.ton.tapreader.classes.apdu.DOL
import stone.ton.tapreader.classes.apdu.TerminalDataBuilder
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
                    val getPPSEApdu = APDUCommand.getForSelectApplication("32 50 41 59 2E 53 59 53 2E 44 44 46 30 31".decodeHex())
                    var receive = transceive(getPPSEApdu)

                    val fciTemplate = parser.parseConstructed(receive.data)
                    Log.i("TESTE", fciTemplate.toString())
                    dumpBerTlv(fciTemplate)
                    val a5 = fciTemplate.find(BerTag(0xA5))
                    val bf0c = a5.find(BerTag(0xbf, 0x0c))
                    val appTemplateList = bf0c.findAll(BerTag(0x61))
                    val candidateList: ArrayList<String> = ArrayList()
                    for(appTemplate in appTemplateList){
                        //0x4F = ApplicationID
                        Log.i("EMV", "Application: " + appTemplate.find(BerTag(0x4f)).hexValue)
                        //0x50 = Application Label
                        Log.i("EMV", "Application: " + appTemplate.find(BerTag(0x50)).textValue)
                        //0x87 = Application Priority Indicator
                        addToApduTrace("PPSE", appTemplate.find(BerTag(0x87)).hexValue +"-" + appTemplate.find(BerTag(0x4f)).hexValue + "-" + appTemplate.find(BerTag(0x50)).textValue)
                        candidateList.add(appTemplate.find(BerTag(0x4f)).hexValue)
                    }
                    val getAppData = APDUCommand.getForSelectApplication(candidateList.get(0).decodeHex())
                    receive = transceive(getAppData)
                    val fciTemplateApp = parser.parseConstructed(receive.data)
                    Log.i("TESTE", fciTemplateApp.toString())
                    dumpBerTlv(fciTemplateApp)

                    val fullPdol: String
                    var pdolValue = "";

                    if(fciTemplateApp.find(BerTag(0xA5)).find(BerTag(0x9F, 0x38))!=null){
                        val PDOL = DOL(fciTemplateApp.find(BerTag(0xA5)).find(BerTag(0x9F, 0x38)).bytesValue)
                        val terminalData = TerminalDataBuilder()
                        for(entry in PDOL.requiredTags){
                            if(terminalData.terminalTags.containsKey(entry.key) && terminalData.terminalTags[entry.key]!!.length == entry.value*2){
                                pdolValue += terminalData.terminalTags[entry.key]
                            }else{
                                pdolValue += "0".repeat(entry.value*2)
                            }
                        }
                        fullPdol = byte2Hex(BerTlvBuilder().addBytes(BerTag(0x83), pdolValue.decodeHex()).buildArray())
                    }else{
                        fullPdol = "8300"
                    }
                    val internalAuthenticate = APDUCommand(
                        class_ = 0x80.toByte(),
                        instruction = 0xA8.toByte(),
                        parameter1 = 0x00,
                        parameter2 = 0x00,
                        data = fullPdol.decodeHex()
                    )
                    receive = transceive(internalAuthenticate)
                    val responseTemplate = parser.parseConstructed(receive.data)
                    Log.i("TESTE", responseTemplate.toString())
                    dumpBerTlv(responseTemplate)
                    val aflValue = responseTemplate.find(BerTag(0x94))
                    val aflEntries = aflValue.hexValue.chunked(8)
                    for(aflEntry in aflEntries){
                        val sfi = aflEntry.subSequence(0,2).toString().decodeHex()[0]
                        val start = aflEntry.subSequence(2,4).toString().decodeHex()[0]
                        val end = aflEntry.subSequence(4,6).toString().decodeHex()[0]
                        val isSigned = aflEntry.subSequence(6,8).toString().decodeHex()[0] > 0x00
                        for(record in start..end){
                            val sfiP2 = (sfi.toInt()) + 4
                            val readRecord = APDUCommand.getForReadRecord(sfiP2.toByte(), record.toByte())
                            receive = transceive(readRecord)
                            val readRecordResponse = parser.parseConstructed(receive.data)
                            Log.i("TESTE", readRecordResponse.toString())
                            dumpBerTlv(readRecordResponse)
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

    fun dumpBerTlv(tlv:BerTlv, level: Int = 0){
        val lvlDump = " ".repeat(level)
        if(tlv.isPrimitive){
            val isAscii: Boolean = isPureAscii(tlv.bytesValue)
            if(isAscii){
                Log.i("TLV", lvlDump + tlv.tag.toString() + " - " + tlv.hexValue + " (" + tlv.textValue + ")")
            }else{
                Log.i("TLV", lvlDump + tlv.tag.toString() + " - " + tlv.hexValue)
            }
        }else{
            Log.i("TLV", lvlDump + tlv.tag.toString())
            for (tag in tlv.values){
                dumpBerTlv(tag, level+1)
            }
        }
    }

    fun clearTrace(@SuppressWarnings("UnusedParameters") view: View) {
        apduTrace!!.text = ""
    }

    private fun addToApduTrace(tag:String, chars: CharSequence){
        apduTrace!!.append("$tag: $chars\n")
    }

    private fun String.decodeHex(): ByteArray {
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
        nfcAdapter.disableForegroundDispatch(this);
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
        return APDUResponse(fullData = recv)
    }

}