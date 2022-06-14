package stone.ton.tapreader.classes.pos.readercomponents.process

import android.util.Log
import stone.ton.tapreader.classes.pos.readercomponents.process.process_d.ProcessDisplay
import stone.ton.tapreader.classes.pos.readercomponents.process.process_d.UserInterfaceRequestData
import stone.ton.tapreader.classes.pos.interfaces.ICardConnection
import stone.ton.tapreader.classes.pos.interfaces.IProcess
import stone.ton.tapreader.classes.pos.readercomponents.process.coprocess.CoProcessPCD

class ProcessMain(
    val processP: ProcessPCD,
    val processD: ProcessDisplay,
    val processS: ProcessSelection
) {
    //TODO implement dataset

    //TODO Stopped at EMV Kernel C8 - 2.2.5
    // 4 Step
    var amount = 0
    var paymentType = ""
    private val languagePreference = UserInterfaceRequestData.Companion.LanguagePreference.PT_BR

    suspend fun startTransaction(amount: Int, paymentType: String) {
        Log.i("ProcessMain", "startTransaction")
        this.amount = amount
        this.paymentType = paymentType
        //processP.startPolling(this::receiveCardDetected)
        CoProcessPCD.cardConnectionCallback = this::receiveCardDetected
        CoProcessPCD.addToQueue("StartPolling");
        processD.processUserInterfaceRequestData(
            UserInterfaceRequestData(
                UserInterfaceRequestData.Companion.MessageIdentifier.PRESENT_CARD,
                UserInterfaceRequestData.Companion.Status.READY_TO_READ,
                0,
                languagePreference,
                UserInterfaceRequestData.Companion.ValueQualifier.AMOUNT,
                amount,
                76
            )
        )
    }

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

    fun initializeKernel() {}

}