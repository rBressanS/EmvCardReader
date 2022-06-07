package stone.ton.tapreader.classes.pos.readercomponents.process

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import stone.ton.tapreader.classes.apdu.APDUCommand
import stone.ton.tapreader.classes.apdu.APDUResponse
import stone.ton.tapreader.classes.pos.interfaces.CardPoller
import stone.ton.tapreader.classes.pos.readercomponents.EmvReaderCallback
import stone.ton.tapreader.classes.utils.General.Companion.toHex
import java.io.IOException

class ProcessPCD(var cardPoller: CardPoller) : IProcess {

    // TODO Process P dataset implementation is not needed due to use of COTS

    lateinit var cardConnection: IsoDep

    fun startPolling(cardConnectionCallback: (IsoDep)->Unit){
        cardPoller.startCardPolling(NfcAdapter.ReaderCallback {
            cardConnection = IsoDep.get(it)
            try {
                cardConnection.connect()
            } catch (e: IOException) {
                println(e)
                Log.i("EMVemulator", "Error tagcomm: " + e.message)
                return@ReaderCallback
            }
            cardConnectionCallback.invoke(cardConnection)
        })
    }


    fun transceive(command: APDUCommand): APDUResponse {
        return transceive(command.getAsBytes().toHex())
    }

    fun transceive(hexstr: String): APDUResponse {
        val hexbytes: Array<String> = hexstr.split(" ").toTypedArray()
        val bytes = ByteArray(hexbytes.size)
        return transceive(bytes)
    }

    fun transceive(bytes: ByteArray): APDUResponse {
        Log.d("EMVemulator", "Send: " + bytes.toHex())
        val recv = cardConnection.transceive(bytes)
        Log.d("EMVemulator", "Received: " + recv.toHex())
        val response = APDUResponse(fullData = recv)
        return response
    }
}