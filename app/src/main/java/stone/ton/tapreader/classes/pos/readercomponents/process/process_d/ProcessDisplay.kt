package stone.ton.tapreader.classes.pos.readercomponents.process.process_d

import stone.ton.tapreader.classes.pos.readercomponents.process.IProcess
import kotlin.math.pow

class ProcessDisplay : IProcess {

    //TODO implement dataset for each language

    var processDisplayConfigData = ProcessDisplayConfigData(
        UserInterfaceRequestData.Companion.LanguagePreference.PT_BR,
        "R$", 2
    )

    fun getFormatedValue(value:Int): String{
        return (value/ 10.0.pow(processDisplayConfigData.minorUnitsForCurrency.toDouble())).toString()
    }

    public fun processUserInterfaceRequestData(uird: UserInterfaceRequestData) {

    }


    companion object {

        data class ProcessDisplayConfigData(
            val defaultLanguage: UserInterfaceRequestData.Companion.LanguagePreference,
            val currencySymbol: String, val minorUnitsForCurrency: Int
        )

    }
}