package stone.ton.tapreader.classes.pos.interfaces

interface IProcess {

    fun processSignal(processFrom:IProcess, signal:String, params:Any?)

    fun signalBuilder(message:String, processFrom:IProcess, params: Any?): IProcessSignal{
        return ProcessSignal(message, params, this, processFrom)
    }

}