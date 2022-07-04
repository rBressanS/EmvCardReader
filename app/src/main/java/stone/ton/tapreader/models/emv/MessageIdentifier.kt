package stone.ton.tapreader.models.emv

enum class MessageIdentifier(val value: Byte) {
    N_A(0X00),
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
    TRY_ANOTHER_CARD(0X1C),
    INSERT_CARD(0X1D),
    NO_MESSAGE(0X1E),
    SEE_PHONE(0X20),
    TRY_AGAIN(0X21);
    companion object {
        fun from(findValue: Byte): MessageIdentifier = values().first { it.value == findValue }
    }
}