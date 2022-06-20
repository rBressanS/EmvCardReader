package stone.ton.tapreader.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.material.composethemeadapter.MdcTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MdcTheme { // or AppCompatTheme
                FullMainActivity()
            }
        }

    }


    @Preview
    @Composable
    private fun FullMainActivity() {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        val items = listOf("credit", "debit")
        var selectedIndex by remember { mutableStateOf(0) }
        var amount by remember { mutableStateOf("12345") }
        Column(modifier = Modifier.padding(15.dp)) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.TopStart)) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { expanded = true }),
                    value = items[selectedIndex],
                    onValueChange = { amount = it },
                    label = { Text("Payment Method") },
                    readOnly = true,
                    enabled = false
                )
                DropdownMenu(
                    modifier = Modifier.fillMaxWidth(),
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    items.forEachIndexed { index, s ->
                        DropdownMenuItem(onClick = {
                            selectedIndex = index
                            expanded = false
                        }) {
                            Text(text = s)
                        }
                    }
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(
                onClick = {
                    val intent = Intent(context, ReadActivity::class.java)
                    intent.putExtra("payment_type", items[selectedIndex])
                    intent.putExtra("amount", amount)
                    startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Read Card")
            }
        }
    }

}