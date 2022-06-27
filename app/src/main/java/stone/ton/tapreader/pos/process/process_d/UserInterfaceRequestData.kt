package stone.ton.tapreader.pos.process.process_d

import stone.ton.tapreader.models.emv.MessageIdentifier

data class UserInterfaceRequestData(
    var messageIdentifier: MessageIdentifier = MessageIdentifier.N_A,
    var status: Status = Status.IDLE,
    var holdTime: Int = 0,
    var languagePreference: LanguagePreference = LanguagePreference.PT,
    var valueQualifier: ValueQualifier = ValueQualifier.AMOUNT,
    var value: Int = 0,
    var currencyCode: Int = 0,
) {
    // Book_A_Architecture_and_General_Rqmts_v2_6
    // Chapter 7.1-User Interface Request
    // Table 7-1


    enum class LanguagePreference(val value:String) {
        PT("pt"), EN("en"), ES("ES");
        companion object {
            fun from(findValue: String): LanguagePreference? = values().firstOrNull { it.value == findValue }
        }
    }

    enum class ValueQualifier {
        AMOUNT, BALANCE
    }

    enum class Status(val value:Byte) {
        NOT_READY(0),
        IDLE(1),
        READY_TO_READ(2),
        PROCESSING(3),
        CARD_READ_SUCCESSFULLY(4),
        PROCESSING_ERROR(5);
        companion object {
            fun from(findValue: Byte): Status = values().first { it.value == findValue }
        }
    }
}