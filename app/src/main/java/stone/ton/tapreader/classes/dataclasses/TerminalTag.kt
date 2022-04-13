package stone.ton.tapreader.classes.dataclasses

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class TerminalTag(
    @SerializedName("tag") @Expose var tag: String,
    @SerializedName("value") @Expose var value: String
)