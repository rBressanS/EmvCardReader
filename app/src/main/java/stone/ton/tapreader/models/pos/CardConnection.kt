package stone.ton.tapreader.models.pos

import android.nfc.tech.IsoDep
import stone.ton.tapreader.interfaces.ICardConnection
import stone.ton.tapreader.models.apdu.APDUCommand
import stone.ton.tapreader.models.apdu.APDUResponse
import stone.ton.tapreader.utils.General.Companion.toHex
import java.util.logging.Logger

class CardConnection(val isoDep: IsoDep) : ICardConnection {

    val logger: Logger = Logger.getLogger(this.javaClass.name)

    override fun connect() {
        return isoDep.connect()
    }

    override fun transceive(command: APDUCommand): APDUResponse {
        return transceive(command.getAsBytes())
    }

    override fun transceive(bytes: ByteArray): APDUResponse {
        logger.info("Send: " + bytes.toHex())
        val begin = System.nanoTime()
        val response = isoDep.transceive(bytes)
        val end = System.nanoTime()
        logger.info("Received: " + response.toHex())
        return APDUResponse(response, end-begin)
    }
}