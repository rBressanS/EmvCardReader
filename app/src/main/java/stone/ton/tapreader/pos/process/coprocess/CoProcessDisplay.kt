package stone.ton.tapreader.pos.process.coprocess

import stone.ton.tapreader.interfaces.IProcess
import stone.ton.tapreader.interfaces.IUIProcessor
import stone.ton.tapreader.models.pos.ProcessSignal
import stone.ton.tapreader.pos.process.process_d.UserInterfaceRequestData
import kotlin.math.pow

object CoProcessDisplay : IProcess {

    lateinit var uiProcessor: IUIProcessor

    //TODO implement dataset for each language

    var processDisplayConfigData = ProcessDisplayConfigData(
        UserInterfaceRequestData.Companion.LanguagePreference.PT_BR,
        "R$", 2
    )

    fun getFormatedValue(value: Int): String {
        return (value / 10.0.pow(processDisplayConfigData.minorUnitsForCurrency.toDouble())).toString()
    }

    private fun processUserInterfaceRequestData(uird: UserInterfaceRequestData) {
        uiProcessor.processUird(uird)
    }

    fun receiveMsgSignal(uird: UserInterfaceRequestData) {

    }


    data class ProcessDisplayConfigData(
        val defaultLanguage: UserInterfaceRequestData.Companion.LanguagePreference,
        val currencySymbol: String, val minorUnitsForCurrency: Int
    )

    fun buildSignalForMsg(processFrom: IProcess, uird: UserInterfaceRequestData): ProcessSignal {
        return ProcessSignal("MSG", uird, this, processFrom)

    }

    override fun processSignal(processFrom: IProcess, signal: String, params: Any?) {
        if (signal == "MSG") {
            if (params is UserInterfaceRequestData) {
                processUserInterfaceRequestData(params)
            }
        }
    }
}