package stone.ton.tapreader

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import stone.ton.tapreader.classes.pos.Pos


class ReadActivity : AppCompatActivity() {

    private var apduTrace: TextView? = null
    private var clearTraceBtn: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read)
        val pos = Pos(this)
        apduTrace = findViewById(R.id.apduTrace)
        clearTraceBtn = findViewById(R.id.clear_text)
        clearTraceBtn!!.setOnClickListener { clearTrace() }
        pos.reader.readCardData()
    }

    fun clearTrace() {
        apduTrace!!.text = ""
    }

    fun addToApduTrace(tag: String, chars: CharSequence) {
        apduTrace!!.append("$tag: $chars\n")
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