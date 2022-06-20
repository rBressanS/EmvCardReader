package stone.ton.tapreader.pos.process.coprocess

import android.util.Log
import com.payneteasy.tlv.BerTag
import stone.ton.tapreader.interfaces.IProcess
import stone.ton.tapreader.interfaces.IProcessSignal
import stone.ton.tapreader.models.apdu.APDUCommand
import stone.ton.tapreader.models.apdu.APDUResponse
import stone.ton.tapreader.models.emv.CandidateApp
import stone.ton.tapreader.models.pos.ProcessSelectionResponse
import stone.ton.tapreader.utils.General.Companion.toHex

object CoProcessSelection : IProcess {

    fun getPPSE(): ProcessSelectionResponse? {
        val ppseResponse =
            CoProcessPCD.communicateWithCard(APDUCommand.buildSelectApplication("32 50 41 59 2E 53 59 53 2E 44 44 46 30 31"))

        val candidateList = parsePPSEResponse(ppseResponse)

        val terminalList = HashMap<String, Int>()
        terminalList["A0000000041010"] = 2
        terminalList["A0000000043060"] = 2
        val selectableApp = pickApplicationFromCandidateList(terminalList, candidateList)
        return if (selectableApp != null) {
            val getAppData = APDUCommand.buildSelectApplication(selectableApp.aid)
            val appResponse = CoProcessPCD.communicateWithCard(getAppData)
            ProcessSelectionResponse(
                selectableApp.kernelId,
                selectableApp.aid.toHex(),
                appResponse
            )
        } else {
            null
        }

    }

    private fun pickApplicationFromCandidateList(
        terminalList: Map<String, Int>,
        cardList: List<CandidateApp>,
    ): CandidateApp? {
        var chosenApp: CandidateApp? = null
        for (cardApp in cardList) {
            val searchAid = cardApp.aid.toHex().replace(" ", "")
            val kernelId = terminalList[searchAid]
            if (kernelId != null) {
                if (chosenApp == null || cardApp.priority[0] < chosenApp.priority[0]) {
                    chosenApp = cardApp
                    chosenApp.kernelId = kernelId
                }
            }
        }
        return chosenApp
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

    private fun receivePPSEResponse(response: APDUResponse) {
        val cardCandidateList = parsePPSEResponse(response)
    }

    override fun processSignal(processFrom: IProcess, signal: String, params: Any?) {
        if (signal == "ACT") {
            getPPSE()
        }
    }

    fun buildSignalForAppSelection(processFrom: IProcess): IProcessSignal {
        return signalBuilder("ACT", processFrom, null)
    }

}