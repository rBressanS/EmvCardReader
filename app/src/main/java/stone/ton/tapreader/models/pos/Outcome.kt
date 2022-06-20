package stone.ton.tapreader.models.pos

enum class Outcome {
    SELECT_NEXT,
    TRY_AGAIN,
    APPROVED,
    DECLINED,
    ONLINE_REQUEST,
    REQUEST_ONLINE_PIN,
    TRY_ANOTHER_INTERFACE,
    END_APPLICATION
}