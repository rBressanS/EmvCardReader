package stone.ton.tapreader.pos.process.process_d

import stone.ton.tapreader.interfaces.IUIProcessor
import kotlin.math.pow

class ProcessDisplay(private var uirdProcessor: IUIProcessor) {

    //TODO implement dataset for each language

    private var processDisplayConfigData = ProcessDisplayConfigData(
        UserInterfaceRequestData.Companion.LanguagePreference.PT_BR,
        "R$", 2
    )

    fun getFormatedValue(value: Int): String {
        return (value / 10.0.pow(processDisplayConfigData.minorUnitsForCurrency.toDouble())).toString()
    }

    fun processUserInterfaceRequestData(uird: UserInterfaceRequestData) {
        uirdProcessor.processUird(uird)
    }

    fun receiveMsgSignal(uird: UserInterfaceRequestData) {

    }


    companion object {

        data class ProcessDisplayConfigData(
            val defaultLanguage: UserInterfaceRequestData.Companion.LanguagePreference,
            val currencySymbol: String, val minorUnitsForCurrency: Int
        )

    }
}