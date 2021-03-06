package stone.ton.tapreader.pos.process

import android.util.Log
import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlvParser
import stone.ton.tapreader.interfaces.ICardConnection
import stone.ton.tapreader.models.apdu.APDUCommand
import stone.ton.tapreader.models.apdu.APDUResponse
import stone.ton.tapreader.models.emv.CandidateApp
import stone.ton.tapreader.models.kernel.KernelData
import stone.ton.tapreader.utils.General.Companion.toHex

class ProcessSelection(private val kernels: List<KernelData>) {
    //TODO improve dataset for each transaction type

    private val parser = BerTlvParser()

    //TODO refactor build of candidate list
    private fun buildCandidateList(
        amount: Int,
        additionalFilter: String = "",
        transactionType: String? = "00",
    ): Map<String, Int> {
        val candidateList = HashMap<String, Int>()
        for (kernel in kernels) {
            for (kernelApp in kernel.kernelApplications) {
                if (additionalFilter != "" && kernelApp.filteringSelector != additionalFilter) {
                    continue
                }
                for (transactionConfig in kernelApp.transactionConfig) {
                    if (transactionConfig.transactionType == transactionType) {
                        if (!transactionConfig.zeroAmountAllowedFlag && amount == 0) {
                            continue
                        }
                        if (transactionConfig.readerContactlessTransactionLimit != null) {
                            if (amount >= transactionConfig.readerContactlessTransactionLimit!!) {
                                continue
                            }
                        }
                    }
                }
                for (aid in kernelApp.aids) {
                    candidateList[aid] = kernel.kernelId
                }
            }
        }
        return candidateList
    }

    fun getSelectionData(
        cardConnection: ICardConnection,
        amount: Int,
        additionalFilter: String
    ): ProcessSelectionResponse? {
        val terminalCandidateList = buildCandidateList(amount, additionalFilter)
        val getPPSEApdu =
            APDUCommand.buildSelectApplication("32 50 41 59 2E 53 59 53 2E 44 44 46 30 31")
        val ppseResponse = cardConnection.transceive(getPPSEApdu)
        val cardCandidateList = parsePPSEResponse(ppseResponse)
        val selectableApp =
            pickApplicationFromCandidateList(terminalCandidateList, cardCandidateList)
        return if (selectableApp != null) {
            val getAppData = APDUCommand.buildSelectApplication(selectableApp.aid)
            val appResponse = cardConnection.transceive(getAppData)
            ProcessSelectionResponse(selectableApp.kernelId, selectableApp.aid.toHex(), appResponse)
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
        val fciTemplate = parser.parseConstructed(receive.data)
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

    companion object {
        data class ProcessSelectionResponse(
            val kenerlId: Int,
            val AID: String,
            val fciResponse: APDUResponse
        )
    }

}