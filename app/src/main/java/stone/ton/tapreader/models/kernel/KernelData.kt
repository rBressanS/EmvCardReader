package stone.ton.tapreader.models.kernel

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import stone.ton.tapreader.models.emv.TerminalTag

data class KernelData(
    @SerializedName("kernelId") @Expose var kernelId: Int,
    @SerializedName("config") @Expose var kernelTags: List<TerminalTag>,
    @SerializedName("kernelConfig") @Expose var kernelConfig: KernelConfig,
    @SerializedName("application") @Expose var kernelApplications: List<KernelApp>
)