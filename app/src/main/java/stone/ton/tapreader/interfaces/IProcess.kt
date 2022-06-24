package stone.ton.tapreader.interfaces

import stone.ton.tapreader.models.pos.ProcessSignal

interface IProcess {

    fun processSignal(processFrom: IProcess, signal: String, params: Any?)

    fun signalBuilder(message: String, processFrom: IProcess, params: Any?): IProcessSignal {
        return ProcessSignal(message, params, this, processFrom)
    }

}