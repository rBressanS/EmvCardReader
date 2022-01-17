package stone.ton.tapreader

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.os.AsyncTask
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlvLogger
import com.payneteasy.tlv.BerTlvParser
import io.github.binaryfoo.RootDecoder
import io.github.binaryfoo.TagInfo
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.function.Consumer

class ReadActivity : AppCompatActivity() {
    private var nfcAdapter /*!< represents the local NFC adapter */: NfcAdapter? = null
    private var tag /*!< represents an NFC tag that has been discovered */: Tag? = null
    private var tagcomm /*!< provides access to ISO-DEP (ISO 14443-4) properties and I/O operations on a Tag */: IsoDep? = null
    private val nfctechfilter = arrayOf(arrayOf(NfcA::class.java.getName()), arrayOf<String>(NfcB::class.java.getName())) /*!<  NFC tech lists */
    private var nfcintent /*!< reference to a token maintained by the system describing the original data used to retrieve it */: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcintent = PendingIntent.getActivity(this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)
    }

    public override fun onResume() {
        /*!
            This method is called when user returns to the activity
         */
        super.onResume()
        //nfcAdapter.enableReaderMode(this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,null);
        nfcAdapter!!.enableForegroundDispatch(this, nfcintent, null, nfctechfilter)
    }


    public override fun onPause() {
        /*!
            This method disable reader mode (enable emulation) when user leave the activity
         */
        super.onPause()
        nfcAdapter!!.disableReaderMode(this)
        //nfcAdapter.disableForegroundDispatch(this);
    }


    override fun onNewIntent(intent: Intent) {
        /*!
            This is called when NFC tag is detected
         */
        super.onNewIntent(intent)
        tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        Log.i("EMVemulator", "Tag detected")
        CardReader().execute(tag)
    }

    /*!
       Inner class that allows to preform card reading in background
       and publish results on the UI thread without having to manipulate threads and handlers
   */
    private inner class CardReader : AsyncTask<Tag?, String?, String?>() {
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
                    Log.i("EMV", "Application: " + appTemplate.find(BerTag(0x50)).getTextValue())
                }
                //myOutWriter.append(byte2Hex(recv).trimIndent())
                var temp: String = "00 A4 04 00 07"
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