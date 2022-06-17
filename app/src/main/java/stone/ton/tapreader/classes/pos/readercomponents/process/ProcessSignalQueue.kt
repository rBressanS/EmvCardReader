package stone.ton.tapreader.classes.pos.readercomponents.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import stone.ton.tapreader.classes.pos.interfaces.IProcessSignal
import java.util.logging.Logger


object ProcessSignalQueue{

    val logger = Logger.getLogger(this.javaClass.name)

    var isStarted = false

    val myQueue = Channel<IProcessSignal>()

    var scope = CoroutineScope(Dispatchers.Default)

    fun start(){
        if(!isStarted){
            logger.info("Queue Started")
            scope.launch{
                for (signal in myQueue) {
                    logger.info("Processing Signal: ${signal.getMessage()} From: ${signal.getProcessFrom()}")
                    signal.getProcessTo().processSignal(signal.getProcessFrom(), signal.getMessage(), signal.getParams())
                }
            }
            isStarted = true
        }
    }

    suspend fun addToQueueAndAwait(processSignal: IProcessSignal){
        myQueue.send(processSignal)
    }

    fun addToQueue(processSignal: IProcessSignal){
        logger.info("Adding Signal to Queue: ${processSignal.getMessage()}" )
        scope.launch {
            myQueue.send(processSignal)
        }
    }

}