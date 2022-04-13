package stone.ton.tapreader.classes.pos.readercomponents.kernels

import android.nfc.tech.IsoDep
import stone.ton.tapreader.classes.apdu.APDUResponse
import stone.ton.tapreader.classes.pos.readercomponents.EPPreProcessingIndicators

interface EmvKernel {

    var cardConnection: IsoDep
    var preProcessingIndicators: EPPreProcessingIndicators
    var selectAppResponse: APDUResponse

    fun getFinalOutcome()

    fun getConfigurationData()

}