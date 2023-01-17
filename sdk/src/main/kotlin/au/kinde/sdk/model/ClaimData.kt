package au.kinde.sdk.model

/**
 * @author roman
 * @since 1.0
 */
sealed class ClaimData {
    open class Organization(val orgCode: String) : ClaimData()
    class Organizations(val orgCodes: List<String>) : ClaimData()
    class Permission(orgCode: String, val isGranted: Boolean) : Organization(orgCode)
    class Permissions(orgCode: String, val permissions: List<String>) : Organization(orgCode)
}