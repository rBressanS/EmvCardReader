package stone.ton.tapreader.models.pos

import stone.ton.tapreader.models.apdu.APDUResponse

data class ProcessSelectionResponse(
    val kenerlId: Int,
    val AID: String,
    val fciResponse: APDUResponse
)
