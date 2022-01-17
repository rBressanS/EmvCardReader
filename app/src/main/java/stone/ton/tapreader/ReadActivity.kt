package stone.ton.tapreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlvParser
import java.io.IOException

class ReadActivity : AppCompatActivity() {
    private lateinit var nfcAdapter: NfcAdapter
    private var tag /*!< represents an NFC tag that has been discovered */: Tag? = null
    private var tagcomm /*!< provides access to ISO-DEP (ISO 14443-4) properties and I/O operations on a Tag */: IsoDep? = null
    private var techListsArray = arrayOf(arrayOf<String>(IsoDep::class.java.name))
    private lateinit var intentFiltersArray: Array<IntentFilter>
    private var nfcintent /*!< reference to a token maintained by the system describing the original data used to retrieve it */: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read)

        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        nfcintent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        intentFiltersArray = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val extra = Bundle()
        extra.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1)
        nfcAdapter.enableReaderMode(this, NfcAdapter.ReaderCallback
            {
                Log.i("Reader Mode",it.toString())
                Log.i("Reader Mode",it.techList.toString())
            },
            (NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK + NfcAdapter.FLAG_READER_NFC_A + NfcAdapter.FLAG_READER_NFC_B),
            extra
        )

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


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        /*!
            This is called when NFC tag is detected
         */
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if(tag != null){
                Log.i("EMVemulator", "Tag detected")
                Log.i("TAG", tag.toString())
                Log.i("TAG", tag!!.techList.toString())
            }
        }
    }

    /*!
       Inner class that allows to preform card reading in background
       and publish results on the UI thread without having to manipulate threads and handlers
   */
    private open inner class CardReader : AsyncTask<Tag?, String?, String?>() {
        var cardtype /*!< string with card type */: String? = null
        var error /*!< string with error value */: String? = null
        override fun doInBackground(vararg params: Tag?): String? {
            /*!
                This method performs background computation (Reading card data) that can take a long time ( up to 60-90 sec)
             */
            val tag = params[0]
            tagcomm = IsoDep.get(tag)
            try {
                tagcomm?.connect()
            } catch (e: IOException) {
                Log.i("EMVemulator", "Error tagcomm: " + e.message)
                error = "Reading card data ... Error tagcomm: " + e.message
                return null
            }
            try {
                readCard()
                tagcomm?.close()
            } catch (e: IOException) {
                Log.i("EMVemulator", "Error tranceive: " + e.message)
                error = "Reading card data ... Error tranceive: " + e.message
                return null
            }
            return null
        }

        private fun readCard() {
            /*!
                This method reads all data from card to perform successful Mag-Stripe
                transaction and saves them to file.
             */
            try {
                //val fOut = openFileOutput("EMV.card", MODE_PRIVATE)
                //val myOutWriter = OutputStreamWriter(fOut)
                var recv = transceive("00 A4 04 00 0E 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00")
                val parser = BerTlvParser()
                val tlvsConstructed = parser.parseConstructed(recv)
                val a5 = tlvsConstructed.find(BerTag(0xA5))
                val bf0c = a5.find(BerTag(0xbf, 0x0c))
                val appTemplates = bf0c.findAll(BerTag(0x61))
                for(appTemplate in appTemplates){
                    Log.i("EMV", "Application: " + appTemplate.find(BerTag(0x4f)).hexValue)
                    Log.i("EMV", "Application: " + appTemplate.find(BerTag(0x50)).textValue)
                }
                //myOutWriter.append(byte2Hex(recv).trimIndent())
                var temp = "00 A4 04 00 07"
                temp += byte2Hex(recv).substring(80, 102)
                temp += "00"

                if (temp.matches(Regex.fromLiteral("00 A4 04 00 07 A0 00 00 00 04 10 10 00"))) cardtype = "MasterCard"
                if (temp.matches(Regex.fromLiteral("00 A4 04 00 07 A0 00 00 00 04 30 60 00"))) cardtype = "Maestro"
                if (temp.matches(Regex.fromLiteral("00 A4 04 00 07 A0 00 00 00 03 20 10 00"))) cardtype = "Visa Electron"
                if (temp.matches(Regex.fromLiteral("00 A4 04 00 07 A0 00 00 00 03 10 10 00"))) cardtype = "Visa"
                recv = transceive(temp)
                //myOutWriter.append(byte2Hex(recv).trimIndent())
                Log.i("EMVemulator", "Done!")
                //myOutWriter.close()
                //fOut.close()
            } catch (e: IOException) {
                Log.i("EMVemulator", "Error readCard: " + e.message)
                error = "Reading card data ... Error readCard: " + e.message
            }
        }

        @kotlin.Throws(IOException::class)
        protected fun transceive(hexstr: String): ByteArray {
            /*!
                This method transceives all the data.
             */
            val hexbytes: Array<String> = hexstr.split(" ").toTypedArray()
            val bytes = ByteArray(hexbytes.size)
            for (i in hexbytes.indices) {
                bytes[i] = hexbytes[i].toInt(16).toByte()
            }
            Log.i("EMVemulator", "Send: " + byte2Hex(bytes))
            val recv = tagcomm!!.transceive(bytes)
            Log.i("EMVemulator", "Received: " + byte2Hex(recv))

            return recv
        }

        protected fun byte2Hex(input: ByteArray): String {
            /*!
            This method converts bytes to strings of hex
         */
            val result = StringBuilder()
            for (inputbyte in input) {
                result.append(String.format("%02X" + " ", inputbyte))
            }
            return result.toString()
        }

        override fun onProgressUpdate(vararg percentage: String?) {
            /*!
                Updates UI thread with progress
             */
            Log.d("NFC","Reading card data ... " + percentage[0] + "%")
        }

        override fun onPreExecute() {
            /*!
                This method update UI thread before the card reading task is executed.
             */
            Log.d("NFC", "Card detected!")
        }

        override fun onPostExecute(result: String?) {
            /*!
                This method update/display results of background card reading when the reading finishes.
             */
            Log.d("NFC", "Reading card data ... completed")
            Log.d("NFC", "Card Type: $cardtype")
        }

    }

}