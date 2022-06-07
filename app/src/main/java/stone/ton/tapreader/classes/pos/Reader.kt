package stone.ton.tapreader.classes.pos

import stone.ton.tapreader.ReadActivity
import stone.ton.tapreader.classes.dataclasses.TerminalTag
import stone.ton.tapreader.classes.dataclasses.kernel.KernelData
import stone.ton.tapreader.classes.pos.readercomponents.EntryPoint
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessMain

class Reader(
    readActivity: ReadActivity,
    kernels: List<KernelData>,
    terminalTags: List<TerminalTag>,
) {

    var processM = ProcessMain()

    val EP = EntryPoint(readActivity, kernels, terminalTags)

    fun readCardData(amount: Int?, paymentType: String?) {
        EP.readCardData(amount!!, paymentType!!)
    }

    fun restartTransactino() {

    }
}