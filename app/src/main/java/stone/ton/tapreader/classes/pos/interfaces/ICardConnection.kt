package stone.ton.tapreader.classes.pos.interfaces

import stone.ton.tapreader.classes.apdu.APDUCommand
import stone.ton.tapreader.classes.apdu.APDUResponse

interface ICardConnection {
    fun connect()

    fun transceive(command: APDUCommand): APDUResponse

    fun transceive(bytes: ByteArray): APDUResponse
}