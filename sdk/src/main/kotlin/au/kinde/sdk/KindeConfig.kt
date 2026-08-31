package au.kinde.sdk

/**
 * Programmatic SDK configuration, passed to [KindeClient.getInstance] as an
 * alternative to the AndroidManifest meta-data keys (`au.kinde.domain`,
 * `au.kinde.clientId`, `au.kinde.audience`). Intended for apps that target
 * multiple Kinde environments (e.g. per-region) from a single binary: persist
 * the user's choice, then supply the matching config in `Application.onCreate`
 * before any other SDK use.
 *
 * When a config is supplied the manifest meta-data is not consulted at all —
 * the keys may be omitted entirely, and a null [audience] means "no audience"
 * rather than "fall back to meta-data".
 *
 * Values are validated when passed to [KindeClient.getInstance]: [domain] must
 * be a bare hostname (no scheme, path, port or whitespace) and [clientId] must
 * contain no whitespace or control characters. This is stricter than the
 * manifest path, matching the per-login `login(domain, clientId)` override.
 *
 * The configuration is fixed for the lifetime of the process: a later
 * [KindeClient.getInstance] call with a different config throws
 * [IllegalStateException], so switching environments means persisting the new
 * choice and restarting the process.
 */
data class KindeConfig @JvmOverloads constructor(
    val domain: String,
    val clientId: String,
    val audience: String? = null
)
