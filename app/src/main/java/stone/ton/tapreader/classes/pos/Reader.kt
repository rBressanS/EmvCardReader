package stone.ton.tapreader.classes.pos

import stone.ton.tapreader.classes.pos.readercomponents.EntryPoint

class Reader {

    val readerContactlessFloorLimit = byteArrayOf(0,0,0,0,0,0)
    val readerContactlessTransactionLimit = byteArrayOf(0,0,0,0,0,0)
    val readerCvmRequiredLimit = byteArrayOf(0,0,0,0,0,0)
    val terminalTransactionQualifier = byteArrayOf(0x00)

    val EP = EntryPoint()

    public fun  startNewTransaction(){

    }

    public fun restartTransactino(){

    }



}