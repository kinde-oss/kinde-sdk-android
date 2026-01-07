package au.kinde.sdk.utils

import android.util.Base64
import au.kinde.sdk.model.ClaimData
import au.kinde.sdk.model.Flag
import au.kinde.sdk.model.FlagType
import au.kinde.sdk.model.TokenType
import au.kinde.sdk.model.UserDetails
import org.json.JSONObject
import kotlin.reflect.KClass
import android.util.Log

/**
 * @author roman
 * @since 1.0
 */
object ClaimDelegate : ClaimApi {

    var tokenProvider: TokenProvider? = null

    override fun getClaim(claim: String, tokenType: TokenType): ClaimData.Claim {
        return ClaimData.Claim(claim, getClaimInternal(claim, tokenType, String::class))
    }

    override fun getUserDetails(): UserDetails = UserDetails(
        getClaimInternal(SUB_CLAIM, TokenType.ID_TOKEN, String::class).orEmpty(),
        getClaimInternal(GIVEN_NAME_CLAIM, TokenType.ID_TOKEN, String::class).orEmpty(),
        getClaimInternal(FAMILY_NAME_CLAIM, TokenType.ID_TOKEN, String::class).orEmpty(),
        getClaimInternal(EMAIL_CLAIM, TokenType.ID_TOKEN, String::class).orEmpty(),
        getClaimInternal(PICTURE_CLAIM, TokenType.ID_TOKEN, String::class).orEmpty()
    )

    override fun getPermissions(): ClaimData.Permissions {
        @Suppress("UNCHECKED_CAST")
        return ClaimData.Permissions(
            getClaimInternal(ORG_CODE_CLAIM, type = String::class).orEmpty(),
            getClaimInternal(PERMISSIONS_CLAIM, type = List::class) as? List<String> ?: emptyList()
        )
    }

    override fun getPermission(permission: String): ClaimData.Permission {
        @Suppress("UNCHECKED_CAST")
        return ClaimData.Permission(
            getClaimInternal(ORG_CODE_CLAIM, type = String::class).orEmpty(),
            (getClaimInternal(PERMISSIONS_CLAIM, type = List::class) as? List<String>
                ?: emptyList())
                .contains(permission)
        )
    }

    override fun getRoles(): ClaimData.Roles {
        @Suppress("UNCHECKED_CAST")
        return ClaimData.Roles(
            getClaimInternal(ORG_CODE_CLAIM, type = String::class).orEmpty(),
            getClaimInternal(ROLES_CLAIM, type = List::class) as? List<String> ?: emptyList()
        )
    }

    override fun getRole(role: String): ClaimData.Role {
        @Suppress("UNCHECKED_CAST")
        return ClaimData.Role(
            getClaimInternal(ORG_CODE_CLAIM, type = String::class).orEmpty(),
            (getClaimInternal(ROLES_CLAIM, type = List::class) as? List<String>
                ?: emptyList())
                .contains(role)
        )
    }

    override fun getUserOrganizations(): ClaimData.Organizations {
        @Suppress("UNCHECKED_CAST")
        return ClaimData.Organizations(
            getClaimInternal(ORG_CODES_CLAIM, TokenType.ID_TOKEN, List::class) as? List<String>
                ?: emptyList()
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
                    return null
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

    private fun <T : Any> getClaimInternal(
        claim: String,
        tokenType: TokenType = TokenType.ACCESS_TOKEN,
        type: KClass<T>
    ): T? {
        return tokenProvider?.getToken(tokenType)?.let { token ->
            val decoded = decodeToken(token)
            val data = JSONObject(decoded)
            if (data.has(claim)) {
                @Suppress("UNCHECKED_CAST")
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

    fun getClaim(claim: String, tokenType: TokenType = TokenType.ACCESS_TOKEN): ClaimData.Claim

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