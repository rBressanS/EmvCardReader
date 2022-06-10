package stone.ton.tapreader.classes.utils

import android.nfc.tech.IsoDep
import stone.ton.tapreader.classes.apdu.APDUCommand
import stone.ton.tapreader.classes.apdu.APDUResponse
import stone.ton.tapreader.classes.pos.interfaces.ICardConnection
import stone.ton.tapreader.classes.utils.General.Companion.toHex
import java.util.logging.Logger

class CardConnection(val isoDep: IsoDep) : ICardConnection {

    val logger = Logger.getLogger(this.javaClass.name)

    override fun connect() {
        return isoDep.connect()
    }

    override fun transceive(command: APDUCommand): APDUResponse {
        return transceive(command.getAsBytes())
    }

    override fun transceive(bytes: ByteArray): APDUResponse {
        logger.info("Send: " + bytes.toHex())
        val recv = isoDep.transceive(bytes)
        logger.info("Received: " + recv.toHex())
        return APDUResponse(fullData = recv)
    }
}