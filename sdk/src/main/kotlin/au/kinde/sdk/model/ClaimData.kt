package au.kinde.sdk.model

/**
 * @author roman
 * @since 1.0
 */
sealed class ClaimData {

    class Claim(val name: String, val value: Any?) : ClaimData()
    open class Organization(val orgCode: String) : ClaimData()
    class Organizations(val orgCodes: List<String>) : ClaimData()
    class Permission(orgCode: String, val isGranted: Boolean) : Organization(orgCode)
    class Permissions(orgCode: String, val permissions: List<String>) : Organization(orgCode)
    class Role(orgCode: String, val isGranted: Boolean) : Organization(orgCode)
    class Roles(orgCode: String, val roles: List<String>) : Organization(orgCode)
}
