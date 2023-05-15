package au.kinde.sdk.model

/**
 * @author roman
 * @since 1.0
 */
data class Flag(
    val code: String,
    val type: FlagType?,
    val value: Any,
    val isDefault: Boolean
)

enum class FlagType(val letter: kotlin.String) {
    String("s"), Integer("i"), Boolean("b");

    companion object {
        fun fromLetter(letter: kotlin.String): FlagType? {
            return values().firstOrNull { flagType -> flagType.letter == letter }
        }
    }
}