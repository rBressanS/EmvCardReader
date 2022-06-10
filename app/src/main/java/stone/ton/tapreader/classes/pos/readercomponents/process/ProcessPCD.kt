package stone.ton.tapreader.classes.pos.readercomponents.process

import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import stone.ton.tapreader.classes.pos.interfaces.ICardPoller
import stone.ton.tapreader.classes.utils.CardConnection
import stone.ton.tapreader.classes.pos.interfaces.ICardConnection
import java.io.IOException
import java.util.logging.Logger

class ProcessPCD(var ICardPoller: ICardPoller) : IProcess {

    val logger = Logger.getLogger(this.javaClass.name)

    // TODO Process P dataset implementation is not needed due to use of COTS

    lateinit var cardConnection: ICardConnection

    fun startPolling(cardConnectionCallback: (ICardConnection) -> Unit) {
        logger.info("startPolling")
        ICardPoller.startCardPolling(NfcAdapter.ReaderCallback {
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


}