package stone.ton.tapreader.interfaces

import stone.ton.tapreader.models.apdu.APDUCommand
import stone.ton.tapreader.models.apdu.APDUResponse

interface ICardConnection {
    fun connect()

    fun transceive(command: APDUCommand): APDUResponse

    fun transceive(bytes: ByteArray): APDUResponse
}