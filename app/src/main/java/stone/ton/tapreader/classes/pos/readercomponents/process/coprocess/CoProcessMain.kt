package stone.ton.tapreader.classes.pos.readercomponents.process.coprocess

import android.util.Log
import stone.ton.tapreader.classes.pos.interfaces.*
import stone.ton.tapreader.classes.pos.readercomponents.process.process_d.UserInterfaceRequestData
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessSignalQueue
import stone.ton.tapreader.classes.pos.readercomponents.process.coprocess.CoProcessPCD.cardPoller

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

    /*
    private fun receiveCardDetected(cardConnection: ICardConnection) {
        val processSelectionResponse =
            processS.getSelectionData(cardConnection, amount, paymentType)
        if(processSelectionResponse == null){
            processD.processUserInterfaceRequestData(
                UserInterfaceRequestData(
                    UserInterfaceRequestData.Companion.MessageIdentifier.USE_ANOTHER_CARD,
                    UserInterfaceRequestData.Companion.Status.READY_TO_READ,
                    0,
                    languagePreference,
                    UserInterfaceRequestData.Companion.ValueQualifier.AMOUNT,
                    amount,
                    76
                )
            )
        }
        this.initializeKernel()
    }
     */

    fun initializeKernel() {}

    override fun processSignal(processFrom: IProcess, signal: String, params: Any?) {
        if(signal == "CardDetected"){
            /*val processSelectionResponse =
                processS.getSelectionData(amount, paymentType)*/
            ProcessSignalQueue.addToQueue(CoProcessDisplay.buildSignalForMsg(this, UserInterfaceRequestData(
                UserInterfaceRequestData.Companion.MessageIdentifier.USE_ANOTHER_CARD,
                UserInterfaceRequestData.Companion.Status.CARD_READ_SUCCESSFULLY,
                0,
                languagePreference,
                UserInterfaceRequestData.Companion.ValueQualifier.AMOUNT,
                amount,
                76
            )))
        }
    }

    public fun buildSignalForCardDetected(processFrom: IProcess): IProcessSignal {
        return ProcessSignal("CardDetected", null, this, processFrom)
    }

}