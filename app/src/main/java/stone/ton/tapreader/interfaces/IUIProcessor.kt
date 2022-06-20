package stone.ton.tapreader.interfaces

import stone.ton.tapreader.pos.process.process_d.UserInterfaceRequestData

interface IUIProcessor {
    fun processUird(userInterfaceRequestData: UserInterfaceRequestData)
}