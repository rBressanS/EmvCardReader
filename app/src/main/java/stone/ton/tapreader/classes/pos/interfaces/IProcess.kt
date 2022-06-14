package stone.ton.tapreader.classes.pos.interfaces

interface IProcess {

    fun processSignal(processFrom:IProcess, signal:String, params:Any)

}