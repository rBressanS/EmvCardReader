package stone.ton.tapreader.classes.pos.readercomponents.process

import stone.ton.tapreader.classes.pos.interfaces.IProcess

class Process:IProcess {
    override fun sendSignal(
        processTo: ProcessSignalQueue.Companion.ProcessType,
        signal: String,
        params: Map<String, Any>
    ) {

    }

}