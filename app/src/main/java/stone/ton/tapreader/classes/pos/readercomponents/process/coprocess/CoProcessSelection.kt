package stone.ton.tapreader.classes.pos.readercomponents.process.coprocess

import android.util.Log
import com.payneteasy.tlv.BerTag
import stone.ton.tapreader.classes.apdu.APDUCommand
import stone.ton.tapreader.classes.apdu.APDUResponse
import stone.ton.tapreader.classes.emv.CandidateApp
import stone.ton.tapreader.classes.pos.interfaces.ICardConnection
import stone.ton.tapreader.classes.pos.interfaces.IProcess
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessSelection
import stone.ton.tapreader.classes.pos.readercomponents.process.ProcessSignalQueue
import stone.ton.tapreader.classes.pos.readercomponents.process.process_d.UserInterfaceRequestData
import stone.ton.tapreader.classes.utils.General.Companion.toHex

object CoProcessSelection: IProcess {

    fun getSelectionData(): ProcessSelection.Companion.ProcessSelectionResponse? {
        val getPPSEApdu =
            APDUCommand.getForSelectApplication("32 50 41 59 2E 53 59 53 2E 44 44 46 30 31")
        val ppseResponse = cardConnection.transceive(getPPSEApdu)
        val cardCandidateList = parsePPSEResponse(ppseResponse)
        val selectableApp =
            pickApplicationFromCandidateList(terminalCandidateList, cardCandidateList)
        return if (selectableApp != null) {
            val getAppData = APDUCommand.getForSelectApplication(selectableApp.aid)
            val appResponse = cardConnection.transceive(getAppData)
            ProcessSelection.Companion.ProcessSelectionResponse(
                selectableApp.kernelId,
                selectableApp.aid.toHex(),
                appResponse
            )
        } else {
            null
        }
    }

    private fun getPPSE(){
        val getPPSEApdu =
            APDUCommand.getForSelectApplication("32 50 41 59 2E 53 59 53 2E 44 44 46 30 31")
            ProcessSignalQueue.addToQueue(CoProcessPCD.buildSignalForCommandApdu(this, getPPSEApdu))
    }

    private fun parsePPSEResponse(receive: APDUResponse): List<CandidateApp> {
        val fciTemplate = receive.getParsedData()
        Log.i("TESTE", fciTemplate.toString())
        val a5 = fciTemplate.find(BerTag(0xA5))
        val bf0c = a5.find(BerTag(0xbf, 0x0c))
        val appTemplateList = bf0c.findAll(BerTag(0x61))
        val candidateList: ArrayList<CandidateApp> = ArrayList()
        for (appTemplate in appTemplateList) {
            val candidateApp = CandidateApp(appTemplate)
            Log.i("EMV", "Application: " + candidateApp.aid.toHex())
            candidateList.add(candidateApp)
        }
        return candidateList
    }

    private fun receivePPSEResponse(response: APDUResponse){
        val cardCandidateList = parsePPSEResponse(response)
    }

    override fun processSignal(processFrom: IProcess, signal: String, params: Any?) {
        if(signal == "ACT"){
            getPPSE()
        }
    }

}