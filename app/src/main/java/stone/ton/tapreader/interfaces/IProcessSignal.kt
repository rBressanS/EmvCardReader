package stone.ton.tapreader.interfaces

interface IProcessSignal {

    fun getMessage(): String

    fun getParams(): Any?

    fun getProcessTo(): IProcess

    fun getProcessFrom(): IProcess
}