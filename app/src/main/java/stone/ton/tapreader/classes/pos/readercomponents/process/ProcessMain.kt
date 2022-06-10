package stone.ton.tapreader.classes.pos.readercomponents.process

import stone.ton.tapreader.classes.pos.readercomponents.process.process_d.ProcessDisplay
import stone.ton.tapreader.classes.pos.readercomponents.process.process_d.UserInterfaceRequestData
import stone.ton.tapreader.classes.pos.interfaces.ICardConnection

class ProcessMain(
    val processP: ProcessPCD,
    val processD: ProcessDisplay,
    val processS: ProcessSelection
) : IProcess {
    //TODO implement dataset

    //TODO Stopped at EMV Kernel C8 - 2.2.5
    // 4 Step
    var amount = 0
    var paymentType = ""
    var languagePreference = UserInterfaceRequestData.Companion.LanguagePreference.PT_BR

    fun startTransaction(amount: Int, paymentType: String) {
        this.amount = amount
        this.paymentType = paymentType
        processP.startPolling(this::receiveCardDetected)
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