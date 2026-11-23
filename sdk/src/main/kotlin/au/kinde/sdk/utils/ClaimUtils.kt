package au.kinde.sdk.utils

import android.util.Base64
import au.kinde.sdk.model.ClaimData
import au.kinde.sdk.model.Flag
import au.kinde.sdk.model.FlagType
import au.kinde.sdk.model.TokenType
import au.kinde.sdk.model.UserDetails
import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.KClass
import android.util.Log

object ClaimDelegate : ClaimApi {

    var tokenProvider: TokenProvider? = null

    override fun getClaim(claim: String, tokenType: TokenType): ClaimData.Claim {
        return ClaimData.Claim(claim, getClaimInternal(claim, tokenType, String::class))
    }

    override fun getClaimValue(claim: String, tokenType: TokenType): Any? {
        val token = tokenProvider?.getToken(tokenType) ?: return null
        val data = runCatching { JSONObject(decodeToken(token)) }.getOrNull() ?: return null
        return if (data.has(claim)) fromJson(data.get(claim)) else null
    }

    /** JSON values as Kotlin ones: objects become maps, arrays lists, JSON null null. */
    private fun fromJson(value: Any?): Any? = when (value) {
        JSONObject.NULL, null -> null
        is JSONObject -> value.keys().asSequence().associateWith { fromJson(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { fromJson(value.get(it)) }
        else -> value
    }

    override fun getUserDetails(): UserDetails = UserDetails(
        getClaimInternal(SUB_CLAIM, TokenType.ID_TOKEN, String::class).orEmpty(),
        getClaimInternal(GIVEN_NAME_CLAIM, TokenType.ID_TOKEN, String::class).orEmpty(),
        getClaimInternal(FAMILY_NAME_CLAIM, TokenType.ID_TOKEN, String::class).orEmpty(),
        getClaimInternal(EMAIL_CLAIM, TokenType.ID_TOKEN, String::class).orEmpty(),
        getClaimInternal(PICTURE_CLAIM, TokenType.ID_TOKEN, String::class).orEmpty()
    )

    override fun getPermissions(): ClaimData.Permissions {
        return ClaimData.Permissions(
            getClaimInternal(ORG_CODE_CLAIM, type = String::class).orEmpty(),
            getStringListClaim(PERMISSIONS_CLAIM)
        )
    }

    override fun getPermission(permission: String): ClaimData.Permission {
        return ClaimData.Permission(
            getClaimInternal(ORG_CODE_CLAIM, type = String::class).orEmpty(),
            getStringListClaim(PERMISSIONS_CLAIM).contains(permission)
        )
    }

    override fun getRoles(): ClaimData.Roles {
        return ClaimData.Roles(
            getClaimInternal(ORG_CODE_CLAIM, type = String::class).orEmpty(),
            getStringListClaim(ROLES_CLAIM)
        )
    }

    override fun getRole(role: String): ClaimData.Role {
        return ClaimData.Role(
            getClaimInternal(ORG_CODE_CLAIM, type = String::class).orEmpty(),
            getStringListClaim(ROLES_CLAIM).contains(role)
        )
    }

    override fun getUserOrganizations(): ClaimData.Organizations {
        return ClaimData.Organizations(
            getStringListClaim(ORG_CODES_CLAIM, TokenType.ID_TOKEN)
        )
    }

    override fun getOrganization(): ClaimData.Organization {
        return ClaimData.Organization(
            getClaimInternal(ORG_CODE_CLAIM, type = String::class).orEmpty()
        )
    }

    private fun decodeToken(jwt: String): String {
        val parts = jwt.split(".")
        return try {
            val bytes = Base64.decode(parts[1], Base64.URL_SAFE)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Error parsing JWT: $e"
        }
    }

    override fun getFlag(code: String, defaultValue: Any?, flagType: FlagType?): Flag? {
        val flagClaim = getClaimInternal(FEATURE_FLAGS_CLAIM, TokenType.ACCESS_TOKEN, String::class)
        val flagsObject = flagClaim?.let { JSONObject(flagClaim) } ?: JSONObject()
        return when {
            !flagsObject.has(code) && defaultValue != null -> Flag(code, null, defaultValue, true)
            flagsObject.has(code) -> {
                val flagObject = flagsObject.getJSONObject(code)
                if (flagType != null && flagObject.getString(FLAG_TYPE) != flagType.letter) {
                    // Same as the API path: a flag of another type falls back to the default.
                    Log.w("KindeClaim", "Flag '$code' type mismatch: expected $flagType, got ${flagObject.getString(FLAG_TYPE)}")
                    return defaultValue?.let { Flag(code, null, it, true) }
                }
                return Flag(
                    code,
                    FlagType.fromLetter(flagObject.getString(FLAG_TYPE)),
                    flagObject.get(FLAG_VALUE),
                    false
                )
            }

            else -> null
        }
    }

    override fun getBooleanFlag(code: String, defaultValue: Boolean?): Boolean? =
        getFlag(code, defaultValue, FlagType.Boolean)?.value as? Boolean?

    override fun getStringFlag(code: String, defaultValue: String?): String? =
        getFlag(code, defaultValue, FlagType.String)?.value as? String?

    override fun getIntegerFlag(code: String, defaultValue: Int?): Int? =
        getFlag(code, defaultValue, FlagType.Integer)?.value as? Int?

    override fun getAllFlags(): Map<String, Flag> {
        val flagClaim = getClaimInternal(FEATURE_FLAGS_CLAIM, TokenType.ACCESS_TOKEN, String::class)
        val flagsObject = flagClaim?.let { JSONObject(flagClaim) } ?: JSONObject()
        val flagsMap = mutableMapOf<String, Flag>()
        
        flagsObject.keys().forEach { code ->
            val flagObject = flagsObject.getJSONObject(code)
            val flag = Flag(
                code,
                FlagType.fromLetter(flagObject.getString(FLAG_TYPE)),
                flagObject.get(FLAG_VALUE),
                false
            )
            flagsMap[code] = flag
        }
        
        return flagsMap
    }

    private fun getStringListClaim(
        claim: String,
        tokenType: TokenType = TokenType.ACCESS_TOKEN
    ): List<String> {
        return getClaimInternal(claim, tokenType, List::class)
            ?.filterIsInstance<String>()
            ?: emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getClaimInternal(
        claim: String,
        tokenType: TokenType = TokenType.ACCESS_TOKEN,
        type: KClass<T>
    ): T? {
        return tokenProvider?.getToken(tokenType)?.let { token ->
            val decoded = decodeToken(token)
            val data = JSONObject(decoded)
            if (data.has(claim)) {
                when (type) {
                    List::class -> {
                        val list = data.getJSONArray(claim)
                        val claims = mutableListOf<String>()
                        for (i in 0 until list.length()) {
                            claims.add(list.getString(i))
                        }
                        claims as T
                    }

                    String::class -> data.getString(claim) as T
                    Int::class -> data.getInt(claim) as T
                    else -> null
                }
            } else {
                Log.w("KindeClaim","The claimed value of \"$claim\" does not exist in your token")
                null
            }
        }
    }

    private const val PERMISSIONS_CLAIM = "permissions"
    private const val ROLES_CLAIM = "roles"
    private const val ORG_CODE_CLAIM = "org_code"
    private const val ORG_CODES_CLAIM = "org_codes"
    private const val SUB_CLAIM = "sub"
    private const val GIVEN_NAME_CLAIM = "given_name"
    private const val FAMILY_NAME_CLAIM = "family_name"
    private const val EMAIL_CLAIM = "email"
    private const val PICTURE_CLAIM = "picture"
    private const val FEATURE_FLAGS_CLAIM = "feature_flags"

    private const val FLAG_TYPE = "t"
    private const val FLAG_VALUE = "v"
}

interface ClaimApi {

    /**
     * The claim's value as text: strings as they are, numbers, booleans, arrays and
     * objects as their JSON. Use [getClaimValue] for the typed value.
     */
    fun getClaim(claim: String, tokenType: TokenType = TokenType.ACCESS_TOKEN): ClaimData.Claim

    /**
     * The claim's value with its JSON type: String, Boolean, Int, Long or Double,
     * List for arrays and Map for objects. Null when the token or claim is missing.
     */
    fun getClaimValue(claim: String, tokenType: TokenType = TokenType.ACCESS_TOKEN): Any?

    fun getUserDetails(): UserDetails

    fun getPermissions(): ClaimData.Permissions

    fun getPermission(permission: String): ClaimData.Permission

    fun getRoles(): ClaimData.Roles

    fun getRole(role: String): ClaimData.Role

    fun getUserOrganizations(): ClaimData.Organizations

    fun getOrganization(): ClaimData.Organization

    fun getFlag(code: String, defaultValue: Any? = null, flagType: FlagType? = null): Flag?

    fun getBooleanFlag(code: String, defaultValue: Boolean? = null): Boolean?

    fun getStringFlag(code: String, defaultValue: String? = null): String?

    fun getIntegerFlag(code: String, defaultValue: Int? = null): Int?

    fun getAllFlags(): Map<String, Flag>
}

interface TokenProvider {
    fun getToken(tokenType: TokenType): String?
}