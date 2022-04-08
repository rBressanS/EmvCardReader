package stone.ton.tapreader.classes.dataclasses.kernel

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import stone.ton.tapreader.classes.dataclasses.TerminalTag

data class KernelApp(
    @SerializedName("aid") @Expose var aids: ArrayList<String>,
    @SerializedName("filteringSelector") @Expose var filteringSelector: String,
    @SerializedName("config") @Expose var appConfig: ArrayList<TerminalTag>,
    @SerializedName("transaction") @Expose var transactionConfig: ArrayList<KernelTransactionConfig>,
) {
}