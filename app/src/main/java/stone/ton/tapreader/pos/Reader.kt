package stone.ton.tapreader.pos

import stone.ton.tapreader.interfaces.ICardPoller
import stone.ton.tapreader.interfaces.IUIProcessor
import stone.ton.tapreader.pos.process.ProcessSignalQueue
import stone.ton.tapreader.pos.process.coprocess.CoProcessDisplay
import stone.ton.tapreader.pos.process.coprocess.CoProcessMain
import stone.ton.tapreader.pos.process.coprocess.CoProcessPCD

class Reader(
    private var cardPoller: ICardPoller,
    private var uiProcessor: IUIProcessor
) {

    fun startByProcess(amount: Int?, paymentType: String?) {
        CoProcessPCD.cardPoller = cardPoller
        CoProcessDisplay.uiProcessor = uiProcessor
        ProcessSignalQueue.start()
        try {
            CoProcessMain.startTransaction(amount!!, paymentType!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        //processM.startTransaction(amount!!, paymentType!!)
    }

    /*val EP = EntryPoint(readActivity, kernels, terminalTags)

    fun readCardData(amount: Int?, paymentType: String?) {
        EP.readCardData(amount!!, paymentType!!)
    }

    fun restartTransactino() {

    }*/

}