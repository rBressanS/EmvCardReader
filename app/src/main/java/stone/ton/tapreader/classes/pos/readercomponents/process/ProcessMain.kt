package stone.ton.tapreader.classes.pos.readercomponents.process

import android.nfc.tech.IsoDep
import stone.ton.tapreader.classes.pos.readercomponents.process.process_d.ProcessDisplay
import stone.ton.tapreader.classes.pos.readercomponents.process.process_d.UserInterfaceRequestData

class ProcessMain : IProcess {
    //TODO implement dataset

    //TODO Stopped at EMV Kernel C8 - 2.2.5
    // 4 Step

    val processP = ProcessPCD()
    val processD = ProcessDisplay()
    val processS = ProcessSelection()

    var amount:Int = 0
    var languagePreference = UserInterfaceRequestData.Companion.LanguagePreference.PT_BR

    fun startTransaction(amount:Int) {
        this.amount = amount
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

    fun receiveCardDetected(cardConnection: IsoDep){
        processS.getSelectionData()

    }

    fun receiveSelectionData(){
        this.initializeKernel()
    }

    fun initializeKernel(){}

}