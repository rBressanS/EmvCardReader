package stone.ton.tapreader.pos.process

import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import stone.ton.tapreader.interfaces.ICardConnection
import stone.ton.tapreader.interfaces.ICardPoller
import stone.ton.tapreader.models.apdu.APDUCommand
import stone.ton.tapreader.models.apdu.APDUResponse
import stone.ton.tapreader.models.pos.CardConnection
import java.io.IOException
import java.util.logging.Logger

class ProcessPCD(private var ICardPoller: ICardPoller) {

    private val logger: Logger = Logger.getLogger(this.javaClass.name)

    // TODO Process P dataset implementation is not needed due to use of COTS

    private lateinit var cardConnection: ICardConnection

    fun receiveCASignal(apduCommand: APDUCommand): Pair<APDUResponse?, CardConnectionStatus?> {
        return try {
            val response = cardConnection.transceive(apduCommand)
            Pair(response, null)
        } catch (e: IOException) {
            logger.severe(e.message)
            logger.severe(e.stackTraceToString())
            Pair(null, CardConnectionStatus.TRANSMISSION_ERROR)
        }

    }

    fun receiveActSignal(): CardConnectionStatus {
        return CardConnectionStatus.CARD_REMOVED
    }

    fun receiveStopSignal(option: StopOptions): CardConnectionStatus {
        return CardConnectionStatus.CARD_REMOVED
    }

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

    companion object {
        enum class StopOptions {
            ABORT, CLOSE_SESSION, CLOSE_SESSION_CARD_CHECK
        }

        enum class CardConnectionStatus {
            CARD_DETECTED, COLLISION_DETECTED, TIMEOUT_ERROR, PROTOCOL_ERROR, TRANSMISSION_ERROR,
            CARD_REMOVED
        }
    }

}