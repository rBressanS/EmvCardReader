package stone.ton.tapreader.classes.pos.interfaces

import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessSignalQueue

interface IProcess {

    fun sendSignal(processTo: ProcessSignalQueue.Companion.ProcessType, signal:String, params:Map<String, Any>)

}