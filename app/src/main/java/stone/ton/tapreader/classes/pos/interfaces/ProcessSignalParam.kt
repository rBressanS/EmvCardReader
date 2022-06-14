package stone.ton.tapreader.classes.pos.interfaces

class ProcessSignalParam<T> {

    inline fun <reified T> getAsObject(params: Any, block: T.() -> Unit) {
        if (params is T) {
            block(params)
        }
    }

}