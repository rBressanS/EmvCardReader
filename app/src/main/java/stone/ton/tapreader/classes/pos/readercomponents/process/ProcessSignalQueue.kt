package stone.ton.tapreader.classes.pos.readercomponents.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import stone.ton.tapreader.classes.pos.interfaces.IProcessSignal


object ProcessSignalQueue{

    val myQueue = Channel<IProcessSignal>()

    var scope = CoroutineScope(Dispatchers.Default)

    fun start(){
        scope.launch{
            for (signal in myQueue) {
                signal.getProcessTo().processSignal(signal.getProcessFrom(), signal.getMessage(), signal.getParams())
            }
        }
    }

}