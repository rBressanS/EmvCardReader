package stone.ton.tapreader.classes.pos.readercomponents.process.process_d

import kotlin.math.pow

data class UserInterfaceRequestData(
    var messageIdentifier: MessageIdentifier,
    var status: Status,
    var holdTime: Int,
    var languagePreference: LanguagePreference,
    var ValueQualifier: ValueQualifier,
    var value: Int,
    var currencyCode: Int
) {
    // Book_A_Architecture_and_General_Rqmts_v2_6
    // Chapter 7.1-User Interface Request
    // Table 7-1


    companion object {
        enum class LanguagePreference {
            PT_BR, EN_US
        }

        enum class ValueQualifier {
            AMOUNT, BALANCE
        }

        enum class Status {
            NOT_READY,
            IDLE,
            READY_TO_READ,
            PROCESSING,
            CARD_READ_SUCCESSFULLY,
            PROCESSING_ERROR
        }

        enum class MessageIdentifier(val messageId: Int) {
            APPROVED(0x03),
            NOT_AUTHORIZED(0X07),
            ENTER_YOUR_PIN(0X09),
            PROCESSING_ERROR(0X0F),
            REMOVE_CARD(0X10),
            WELCOME(0X14),
            PRESENT_CARD(0X15),
            PROCESSING(0X16),
            CARD_READ_OK(0X17),
            INSERT_OR_SWIPE_CARD(0X18),
            PRESENT_ONE_CARD_ONLY(0X19),
            APPROVED_SIGN(0X1A),
            AUTHORISING_PLEASE_WAIT(0X1B),
            USE_ANOTHER_CARD(0X1C),
            INSERT_CARD(0X1D),
            NO_MESSAGE(0X1E),
            SEE_PHONE(0X20),
            PRESENT_CARD_AGAIN(0X21),
        }
    }
}