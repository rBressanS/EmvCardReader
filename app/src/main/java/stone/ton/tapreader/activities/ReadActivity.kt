package stone.ton.tapreader.activities

import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import stone.ton.tapreader.R
import stone.ton.tapreader.interfaces.ICardPoller
import stone.ton.tapreader.interfaces.IUIProcessor
import stone.ton.tapreader.pos.Pos
import stone.ton.tapreader.pos.process.process_d.UserInterfaceRequestData

class ReadActivity : AppCompatActivity(), ICardPoller, IUIProcessor {

    private lateinit var apduTrace: TextView
    private lateinit var clearTraceBtn: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val amount = intent.getStringExtra("amount")
        val paymentType = intent.getStringExtra("payment_type")
        setContentView(R.layout.activity_read)
        val pos = Pos(this, this, this)
        apduTrace = findViewById(R.id.apduTrace)
        clearTraceBtn = findViewById(R.id.clear_text)
        clearTraceBtn.setOnClickListener { clearTrace() }
        pos.reader.startByProcess(Integer.parseInt(amount!!, 10), paymentType)
        apduTrace.movementMethod = ScrollingMovementMethod()
    }

    private fun clearTrace() {
        apduTrace.text = ""
    }

    private fun addToApduTrace(tag: String, chars: CharSequence) {
        val newText = apduTrace.text.toString() + "\n" + tag + ":" + chars
        apduTrace.text = newText
    }

    private var onResumeCallback: (() -> Unit)? = null

    public override fun onResume() {
        /*!
            This method is called when user returns to the activity
         */
        super.onResume()
        onResumeCallback?.invoke()
    }

    private var onPauseCallback: (() -> Unit)? = null

    public override fun onPause() {
        /*!
            This method disable reader mode (enable emulation) when user leave the activity
         */
        super.onPause()
        onPauseCallback?.invoke()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun startCardPolling(readerCallback: NfcAdapter.ReaderCallback) {

        Log.i("ReadActivity", "StartCardPolling")

        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val extra = Bundle()
        extra.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000)
        this.onResumeCallback = {

            nfcAdapter.enableReaderMode(
                this,
                readerCallback,
                (NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK + NfcAdapter.FLAG_READER_NFC_A + NfcAdapter.FLAG_READER_NFC_B),
                extra
            )
        }
        this.onPauseCallback = {
            nfcAdapter.disableReaderMode(this)
        }

        this.onResumeCallback!!.invoke()
    }

    override fun processUird(userInterfaceRequestData: UserInterfaceRequestData) {
        addToApduTrace("User Message", userInterfaceRequestData.messageIdentifier.name)
    }

}