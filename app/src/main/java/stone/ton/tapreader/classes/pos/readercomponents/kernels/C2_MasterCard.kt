package stone.ton.tapreader.classes.pos.readercomponents.kernels

import android.nfc.tech.IsoDep
import stone.ton.tapreader.classes.apdu.APDUResponse
import stone.ton.tapreader.classes.pos.readercomponents.EPPreProcessingIndicators

class C2_MasterCard: EmvKernel {
    override var cardConnection: IsoDep
        get() = TODO("Not yet implemented")
        set(value) {}
    override var preProcessingIndicators: EPPreProcessingIndicators
        get() = TODO("Not yet implemented")
        set(value) {}
    override var selectAppResponse: APDUResponse
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun getFinalOutcome() {
        TODO("Not yet implemented")
    }

    override fun getConfigurationData() {
        TODO("Not yet implemented")
    }
}