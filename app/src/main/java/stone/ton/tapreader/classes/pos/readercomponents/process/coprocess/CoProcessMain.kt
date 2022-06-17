package stone.ton.tapreader.classes.pos.readercomponents.process.coprocess

import android.util.Log
import stone.ton.tapreader.classes.pos.interfaces.*
import stone.ton.tapreader.classes.pos.readercomponents.process.process_d.UserInterfaceRequestData
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessSignalQueue
import stone.ton.tapreader.classes.pos.readercomponents.process.coprocess.CoProcessPCD.cardPoller
import stone.ton.tapreader.classes.utils.DataSets

object CoProcessMain:IProcess {
    //TODO implement dataset

    //TODO Stopped at EMV Kernel C8 - 2.2.5
    // 4 Step

    var amount = 0
    var paymentType = ""
    private val languagePreference = UserInterfaceRequestData.Companion.LanguagePreference.PT_BR

    fun startTransaction(amount: Int, paymentType: String) {
        Log.i("ProcessMain", "startTransaction")
        this.amount = amount
        this.paymentType = paymentType

        ProcessSignalQueue.addToQueue(CoProcessPCD.buildSignalForPolling(this, cardPoller))

        ProcessSignalQueue.addToQueue(CoProcessDisplay.buildSignalForMsg(this, UserInterfaceRequestData(
            UserInterfaceRequestData.Companion.MessageIdentifier.PRESENT_CARD,
            UserInterfaceRequestData.Companion.Status.READY_TO_READ,
            0,
            languagePreference,
            UserInterfaceRequestData.Companion.ValueQualifier.AMOUNT,
            amount,
            76
        )))
    }

    fun initializeKernel() {}

    override fun processSignal(processFrom: IProcess, signal: String, params: Any?) {
        if(signal == "CardDetected"){

            val response = CoProcessSelection.getPPSE()
            if (response != null) {
                if(response.kenerlId == 2){
                    val kernelData = DataSets.kernels.find { kernelData -> kernelData.kernelId == 2 }
                    if (kernelData != null) {
                        CoProcessKernelC2.kernelData = kernelData
                        CoProcessKernelC2.start(response.fciResponse)
                    }
                }
            }

        }
    }

    public fun buildSignalForCardDetected(processFrom: IProcess): IProcessSignal {
        return ProcessSignal("CardDetected", null, this, processFrom)
    }

}