package stone.ton.tapreader.classes.pos.interfaces

interface IProcessSignal {

    fun getMessage():String

    fun getParams():Any?

    fun getProcessTo(): IProcess

    fun getProcessFrom(): IProcess
}