package au.kinde.sdk.keys

import com.google.gson.annotations.SerializedName

/**
 * @author roman
 * @since 1.0
 */
data class Keys(
    val keys:List<Key>
)

data class Key(
    @SerializedName("e") val exponent:String,
    @SerializedName("n") val modulus:String,
    @SerializedName("alg") val signingAlgorithm:String,
    @SerializedName("kid") val keyId:String,
    @SerializedName("kty") val keyType:String,
)