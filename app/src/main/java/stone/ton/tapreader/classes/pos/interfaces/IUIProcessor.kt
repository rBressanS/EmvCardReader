package stone.ton.tapreader.classes.pos.interfaces

import stone.ton.tapreader.classes.pos.readercomponents.process.process_d.UserInterfaceRequestData

interface IUIProcessor {
    fun processUird(userInterfaceRequestData: UserInterfaceRequestData)
}