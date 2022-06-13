package stone.ton.tapreader.classes.pos.readercomponents.process

import java.util.Queue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class ProcessSignalQueue{
    companion object{

        val myQueue = ArrayBlockingQueue<SignalMessage>(9)

        data class SignalMessage(val processFrom:ProcessType, val processTo: ProcessType, val signal:String, val params:Map<String, Any>)
        enum class ProcessType{
            PROCESS_D, PROCESS_P, PROCESS_S
        }
    }

}