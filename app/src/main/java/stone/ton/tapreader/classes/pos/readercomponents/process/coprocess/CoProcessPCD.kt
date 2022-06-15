package stone.ton.tapreader.classes.pos.readercomponents.process.coprocess

import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import com.payneteasy.tlv.BerTlvParser
import stone.ton.tapreader.classes.apdu.APDUCommand
import stone.ton.tapreader.classes.pos.interfaces.*
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessSignalQueue
import stone.ton.tapreader.classes.utils.CardConnection
import java.io.IOException
import java.util.logging.Logger

object CoProcessPCD: IProcess {

    val logger = Logger.getLogger(this.javaClass.name)

    private val parser = BerTlvParser()

    lateinit var cardPoller: ICardPoller

    lateinit var cardConnection: ICardConnection

    private fun startPolling(cardPoller: ICardPoller) {
        logger.info("startPolling")
        cardPoller.startCardPolling(NfcAdapter.ReaderCallback {
            cardConnection = CardConnection(IsoDep.get(it))
            try {
                cardConnection.connect()
                logger.info("connected")
                ProcessSignalQueue.addToQueue(CoProcessMain.buildSignalForCardDetected(this))
            } catch (e: IOException) {
                println(e)
                logger.warning("Error tagcomm: " + e.message)
                return@ReaderCallback
            }
        })
    }


    override fun processSignal(processFrom: IProcess, signal: String, params: Any?) {
        if(signal == "StartPolling"){
            if(params is ICardPoller){
                startPolling(params)
            }else{
                throw RuntimeException("Invalid param")
            }
        }
        if(signal == "ACT"){
            if(params is APDUCommand){
                var response = cardConnection.transceive(params)

            }
        }
    }

    fun buildSignalForPolling(processFrom: IProcess, cardPoller: ICardPoller): IProcessSignal{
        return signalBuilder("StartPolling", processFrom, cardPoller)
    }

    fun buildSignalForCommandApdu(processFrom:IProcess, apduCommand:APDUCommand): IProcessSignal{
        return signalBuilder("ACT", processFrom, apduCommand)
    }

}