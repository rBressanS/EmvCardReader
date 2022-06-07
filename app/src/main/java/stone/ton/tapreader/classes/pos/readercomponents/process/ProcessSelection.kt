package stone.ton.tapreader.classes.pos.readercomponents.process

import stone.ton.tapreader.classes.apdu.APDUResponse

class ProcessSelection: IProcess  {

    //TODO implement dataset for each transaction type

    public fun getSelectionData(): ProcessSelectionResponse?{
        return null//TODO
    }

    companion object{
        data class ProcessSelectionResponse(val kenerlId:Int, val AID:String, val fciResponse:APDUResponse)
    }

}