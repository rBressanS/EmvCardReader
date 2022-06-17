package stone.ton.tapreader.classes.utils

import stone.ton.tapreader.classes.apdu.APDUResponse

data class ProcessSelectionResponse(
    val kenerlId: Int,
    val AID: String,
    val fciResponse: APDUResponse
)
