package stone.ton.tapreader.pos.process.coprocess

import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import com.payneteasy.tlv.BerTlvParser
import stone.ton.tapreader.interfaces.ICardConnection
import stone.ton.tapreader.interfaces.ICardPoller
import stone.ton.tapreader.interfaces.IProcess
import stone.ton.tapreader.interfaces.IProcessSignal
import stone.ton.tapreader.models.apdu.APDUCommand
import stone.ton.tapreader.models.apdu.APDUResponse
import stone.ton.tapreader.models.pos.CardConnection
import stone.ton.tapreader.models.pos.ProcessSignal
import stone.ton.tapreader.pos.process.ProcessSignalQueue
import java.io.IOException
import java.util.logging.Logger

object CoProcessPCD : IProcess {

    private val logger: Logger = Logger.getLogger(this.javaClass.name)

    private val parser = BerTlvParser()

    lateinit var cardPoller: ICardPoller

    private lateinit var cardConnection: ICardConnection

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
        if (signal == "StartPolling") {
            if (params is ICardPoller) {
                startPolling(params)
            } else {
                throw RuntimeException("Invalid param")
            }
        }
        if (signal == "ACT") {
            if (params is APDUCommand) {
                val response = cardConnection.transceive(params)
                ProcessSignalQueue.addToQueue(ProcessSignal("OUT", response, processFrom, this))
            }
        }
    }

    fun communicateWithCard(params: APDUCommand): APDUResponse {
        return cardConnection.transceive(params)
    }

    fun buildSignalForPolling(processFrom: IProcess, cardPoller: ICardPoller): IProcessSignal {
        return signalBuilder("StartPolling", processFrom, cardPoller)
    }

    fun buildSignalForCommandApdu(processFrom: IProcess, apduCommand: APDUCommand): IProcessSignal {
        return signalBuilder("ACT", processFrom, apduCommand)
    }

}