package stone.ton.tapreader.models.pos

import stone.ton.tapreader.interfaces.IProcess
import stone.ton.tapreader.interfaces.IProcessSignal

class ProcessSignal(
    private val message: String,
    private val params: Any?,
    private val processTo: IProcess,
    private val processFrom: IProcess
) :
    IProcessSignal {

    override fun getMessage(): String {
        return message
    }

    override fun getParams(): Any? {
        return params
    }

    override fun getProcessTo(): IProcess {
        return processTo
    }

    override fun getProcessFrom(): IProcess {
        return processFrom
    }

}