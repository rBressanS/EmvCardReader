package stone.ton.tapreader.classes.dataclasses

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class CaPublicKey(
    @SerializedName("hashAlgorithmIndicator") @Expose var hashAlgorithmIndicator: Int,
    @SerializedName("publicKeyAlgorithmIndicator") @Expose var publicKeyAlgorithmIndicator: Int,
    @SerializedName("modulus") @Expose var modulus: String,
    @SerializedName("exponent") @Expose var exponent: String,
    @SerializedName("expiryDate") @Expose var expiryDate: String,
    @SerializedName("index") @Expose var index: String,
    @SerializedName("checksum") @Expose var checksum: String,
    @SerializedName("rId") @Expose var rId: String
)