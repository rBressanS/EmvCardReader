package stone.ton.tapreader.models.kernel

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import stone.ton.tapreader.models.emv.TerminalTag

data class KernelTransactionConfig(
    @SerializedName("transactionType") @Expose var transactionType: String,
    @SerializedName("statusCheckSupportFlag") @Expose var statusCheckSupportFlag: Boolean,
    @SerializedName("zeroAmountAllowedFlag") @Expose var zeroAmountAllowedFlag: Boolean,
    @SerializedName("extendedSelectionSupportFlag") @Expose var extendedSelectionSupportFlag: Boolean,
    @SerializedName("partialSelectionSupportFlag") @Expose var partialSelectionSupportFlag: Boolean,
    @SerializedName("ppseSelectionFallbackSupportFlag") @Expose var ppseSelectionFallbackSupportFlag: Boolean? = null,
    @SerializedName("readerContactlessFloorLimit") @Expose var readerContactlessFloorLimit: Int,
    @SerializedName("readerContactlessTransactionLimit") @Expose var readerContactlessTransactionLimit: Int?,
    @SerializedName("readerCVMRequiredLimit") @Expose var readerCVMRequiredLimit: Int,
    @SerializedName("config") @Expose var transactionConfig: ArrayList<TerminalTag>
)