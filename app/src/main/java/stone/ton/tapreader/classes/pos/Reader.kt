package stone.ton.tapreader.classes.pos

import stone.ton.tapreader.ReadActivity
import stone.ton.tapreader.classes.dataclasses.TerminalTag
import stone.ton.tapreader.classes.dataclasses.kernel.KernelData
import stone.ton.tapreader.classes.pos.readercomponents.EntryPoint

class Reader(readActivity: ReadActivity, kernels: List<KernelData>, terminalTags: List<TerminalTag>) {

    val readerContactlessFloorLimit = byteArrayOf(0, 0, 0, 0, 0, 0)
    val readerContactlessTransactionLimit = byteArrayOf(0, 0, 0, 0, 0, 0)
    val readerCvmRequiredLimit = byteArrayOf(0, 0, 0, 0, 0, 0)
    val terminalTransactionQualifier = byteArrayOf(0x00)

    val EP = EntryPoint(readActivity, kernels, terminalTags)

    fun readCardData() {
        EP.readCardData()
    }

    fun restartTransactino() {

    }


}