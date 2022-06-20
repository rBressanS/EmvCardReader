package stone.ton.tapreader.models.kernel

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class KernelConfig(
    @SerializedName("terminalEntryCapability") @Expose var terminalEntryCapability: Int? = null,
    @SerializedName("fddaForOnlineSupported") @Expose var fddaForOnlineSupported: Boolean? = null,
    @SerializedName("displayAvailableSpendingAmount") @Expose var displayAvailableSpendingAmount: Boolean? = null,
    @SerializedName("aucManualCheckSupported") @Expose var aucManualCheckSupported: Boolean? = null,
    @SerializedName("aucCashbackCheckSupported") @Expose var aucCashbackCheckSupported: Boolean? = null,
    @SerializedName("exceptionFileEnabled") @Expose var exceptionFileEnabled: Boolean? = null,
    @SerializedName("odaEnabled") @Expose var odaEnabled: Boolean? = null,
    @SerializedName("tacDefault") @Expose var tacDefault: String? = null,
    @SerializedName("tacOnline") @Expose var tacOnline: String? = null,
    @SerializedName("tacDenial") @Expose var tacDenial: String? = null,
)