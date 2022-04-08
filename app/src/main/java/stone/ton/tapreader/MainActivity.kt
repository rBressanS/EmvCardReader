package stone.ton.tapreader

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener { goToReadCard() }

    }

    fun goToReadCard() {
        val intent = Intent(this, ReadActivity::class.java)
        val spinner = findViewById<Spinner>(R.id.spinnerPaymentType)
        val amount = findViewById<EditText>(R.id.numberAmount)
        intent.putExtra("payment_type", spinner.selectedItem.toString())
        intent.putExtra("amount", amount.text.toString())
        startActivity(intent)
    }
}