package stone.ton.tapreader.classes.pos.readercomponents.process.coprocess

import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import stone.ton.tapreader.classes.pos.interfaces.ICardConnection
import stone.ton.tapreader.classes.pos.interfaces.ICardPoller
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessSignalQueue
import stone.ton.tapreader.classes.utils.CardConnection
import java.io.IOException
import java.util.logging.Logger

object CoProcessPCD {

    val logger = Logger.getLogger(this.javaClass.name)

    var scope = CoroutineScope(Dispatchers.Default)

    lateinit var cardPoller: ICardPoller

    lateinit var cardConnection: ICardConnection

    private val channel = Channel<ProcessSignalQueue.Companion.SignalMessage>()

    lateinit var cardConnectionCallback: (ICardConnection) -> Unit;

    private fun startPolling() {
        logger.info("startPolling")
        cardPoller.startCardPolling(NfcAdapter.ReaderCallback {
            cardConnection = CardConnection(IsoDep.get(it))
            try {
                cardConnection.connect()
                logger.info("connected")
            } catch (e: IOException) {
                println(e)
                logger.warning("Error tagcomm: " + e.message)
                return@ReaderCallback
            }
            cardConnectionCallback.invoke(cardConnection)
        })
    }

    fun start(){
        Log.i("CoProcessPCD", "start")
        scope.launch{
            for (y in channel) {
                Log.i("CoProcessPCD", "ReadFromQueue")
                if(y.signal == "StartPolling"){
                    startPolling();
                }
            }
        }

    }

    suspend fun addToQueue(signal: String){
        Log.i("CoProcessPCD", "addToQueue")
        channel.send(ProcessSignalQueue.Companion.SignalMessage(ProcessSignalQueue.Companion.ProcessType.PROCESS_S, ProcessSignalQueue.Companion.ProcessType.PROCESS_P, signal, HashMap()))
    }




}