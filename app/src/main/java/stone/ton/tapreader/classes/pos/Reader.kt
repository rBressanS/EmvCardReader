package stone.ton.tapreader.classes.pos

import stone.ton.tapreader.ReadActivity
import stone.ton.tapreader.classes.dataclasses.TerminalTag
import stone.ton.tapreader.classes.dataclasses.kernel.KernelData
import stone.ton.tapreader.classes.pos.interfaces.ICardPoller
import stone.ton.tapreader.classes.pos.interfaces.IUIProcessor
import stone.ton.tapreader.classes.pos.readercomponents.EntryPoint
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessMain
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessPCD
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessSelection
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessSignalQueue
import stone.ton.tapreader.classes.pos.readercomponents.process.coprocess.CoProcessDisplay
import stone.ton.tapreader.classes.pos.readercomponents.process.coprocess.CoProcessMain
import stone.ton.tapreader.classes.pos.readercomponents.process.coprocess.CoProcessPCD
import stone.ton.tapreader.classes.pos.readercomponents.process.process_d.ProcessDisplay

class Reader(
    readActivity: ReadActivity,
    kernels: List<KernelData>,
    terminalTags: List<TerminalTag>,
    var cardPoller: ICardPoller,
    var uiProcessor: IUIProcessor
) {

    fun startByProcess(amount: Int?, paymentType: String?){
        CoProcessPCD.cardPoller = cardPoller
        CoProcessDisplay.uiProcessor = uiProcessor
        ProcessSignalQueue.start()
        CoProcessMain.startTransaction(amount!!, paymentType!!)
        //processM.startTransaction(amount!!, paymentType!!)
    }

    /*val EP = EntryPoint(readActivity, kernels, terminalTags)

    fun readCardData(amount: Int?, paymentType: String?) {
        EP.readCardData(amount!!, paymentType!!)
    }

    fun restartTransactino() {

    }*/

}