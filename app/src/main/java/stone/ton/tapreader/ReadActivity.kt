package stone.ton.tapreader

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import stone.ton.tapreader.classes.pos.Pos


class ReadActivity : AppCompatActivity() {

    private lateinit var apduTrace: TextView
    private lateinit var clearTraceBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var amount = intent.getStringExtra("amount")
        var paymentType = intent.getStringExtra("payment_type")
        setContentView(R.layout.activity_read)
        val pos = Pos(this)
        apduTrace = findViewById<TextView>(R.id.apduTrace)
        clearTraceBtn = findViewById(R.id.clear_text)
        clearTraceBtn.setOnClickListener { clearTrace() }
        pos.reader.readCardData(Integer.parseInt(amount!!,10), paymentType)
        apduTrace.movementMethod = ScrollingMovementMethod()
    }

    fun clearTrace() {
        apduTrace.text = ""
    }

    fun addToApduTrace(tag: String, chars: CharSequence) {
        val newText = apduTrace.text.toString() + "\n" + tag + ":" + chars
        apduTrace.text = newText
        //val editable: Editable = apduTrace.editableText
        //Selection.setSelection(editable, editable.length)
    }

    var onResumeCallback: (() -> Unit)? = null

    public override fun onResume() {
        /*!
            This method is called when user returns to the activity
         */
        super.onResume()
        onResumeCallback?.invoke()
    }

    var onPauseCallback: (() -> Unit)? = null

    public override fun onPause() {
        /*!
            This method disable reader mode (enable emulation) when user leave the activity
         */
        super.onPause()
        onPauseCallback?.invoke()
    }

}