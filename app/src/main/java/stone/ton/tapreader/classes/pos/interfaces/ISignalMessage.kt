package stone.ton.tapreader.classes.pos.interfaces

import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessSignalQueue

interface ISignalMessage {

    val processFrom: ProcessSignalQueue.Companion.ProcessType
    val processTo: ProcessSignalQueue.Companion.ProcessType
    val signal:String
    val params:Map<String, Any>
}