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
import stone.ton.tapreader.classes.pos.interfaces.IProcess
import stone.ton.tapreader.classes.pos.interfaces.IProcessSignalParam
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessSignalQueue
import stone.ton.tapreader.classes.utils.CardConnection
import java.io.IOException
import java.util.logging.Logger

object CoProcessPCD: IProcess {

    val logger = Logger.getLogger(this.javaClass.name)

    lateinit var cardPoller: ICardPoller

    lateinit var cardConnection: ICardConnection

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

    private fun startPolling(cardPoller: ICardPoller) {
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


    override fun processSignal(processFrom: IProcess, signal: String, params: Any) {
        if(signal == "StartPolling"){

            startPolling();
        }
    }

    class

}