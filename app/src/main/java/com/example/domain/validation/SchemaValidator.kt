package com.example.domain.validation

data class ValidationError(
    val field: String,
    val message: String
)

class ValidationException(
    val errors: List<ValidationError>
) : IllegalArgumentException(
    errors.joinToString("; ") { "${it.field}: ${it.message}" }
) {
    fun fieldMessages(): Map<String, String> =
        errors.associate { it.field to it.message }
}

sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>
    data class Invalid(val errors: List<ValidationError>) : ValidationResult<Nothing> {
        fun fieldMessages(): Map<String, String> =
            errors.associate { it.field to it.message }
    }

    fun getOrThrow(): T = when (this) {
        is Valid -> value
        is Invalid -> throw ValidationException(errors)
    }

    fun toResult(): Result<T> = when (this) {
        is Valid -> Result.success(value)
        is Invalid -> Result.failure(ValidationException(errors))
    }
}

class ValidationScope {
    private val _errors = mutableListOf<ValidationError>()
    val errors: List<ValidationError> get() = _errors

    fun addError(field: String, message: String) {
        _errors.add(ValidationError(field, message))
    }

    fun stringField(
        field: String,
        value: String?,
        minLen: Int = 1,
        maxLen: Int,
        label: String = field,
        pattern: Regex? = null,
        patternMessage: String? = null
    ): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.length < minLen) {
            addError(
                field,
                if (minLen <= 1) "$label is required"
                else "$label must be at least $minLen characters"
            )
            return trimmed
        }
        if (trimmed.length > maxLen) {
            addError(field, "$label must be $maxLen characters or fewer")
            return trimmed
        }
        if (pattern != null && !pattern.matches(trimmed)) {
            addError(field, patternMessage ?: "$label contains unsupported characters")
        }
        return trimmed
    }

    fun optionalStringField(
        field: String,
        value: String?,
        maxLen: Int,
        label: String = field,
        pattern: Regex? = null,
        patternMessage: String? = null
    ): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (trimmed.length > maxLen) {
            addError(field, "$label must be $maxLen characters or fewer")
        } else if (pattern != null && !pattern.matches(trimmed)) {
            addError(field, patternMessage ?: "$label contains unsupported characters")
        }
        return trimmed
    }

    fun boundedZeroOrMoreString(
        field: String,
        value: String?,
        maxLen: Int,
        label: String = field
    ): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.length > maxLen) {
            addError(field, "$label must be $maxLen characters or fewer")
        }
        return trimmed
    }

    fun longRange(
        field: String,
        value: Long,
        min: Long,
        max: Long,
        label: String = field
    ): Long {
        if (value < min || value > max) {
            val rangeDesc = if (min == 1L) "greater than zero (max $max)" else "between $min and $max"
            addError(field, "$label must be $rangeDesc")
        }
        return value
    }

    fun oneOf(
        field: String,
        value: String,
        allowed: Set<String>,
        label: String = field
    ): String {
        val trimmed = value.trim()
        if (trimmed !in allowed) {
            addError(field, "$label '$trimmed' is not recognized")
        }
        return trimmed
    }

    fun stringList(
        field: String,
        values: List<String>,
        minSize: Int = 0,
        maxSize: Int,
        itemMinLen: Int = 1,
        itemMaxLen: Int = 128,
        deduplicate: Boolean = true,
        label: String = field
    ): List<String> {
        val cleaned = values.map { it.trim() }
            .filter { it.isNotBlank() }
            .let { if (deduplicate) it.distinct() else it }
        if (cleaned.size < minSize) {
            addError(field, "$label must include at least $minSize item(s)")
        }
        if (cleaned.size > maxSize) {
            addError(field, "$label cannot exceed $maxSize item(s)")
        }
        cleaned.forEachIndexed { idx, item ->
            if (item.length < itemMinLen || item.length > itemMaxLen) {
                addError("$field[$idx]", "$label entry '${item.take(24)}' must be $itemMinLen-$itemMaxLen characters")
            }
        }
        return cleaned
    }

    fun refine(field: String, condition: Boolean, message: String) {
        if (!condition) {
            addError(field, message)
        }
    }
}

fun interface Schema<T> {
    fun parse(input: T): ValidationResult<T>

    fun parseOrThrow(input: T): T = parse(input).getOrThrow()

    fun parseAsResult(input: T): Result<T> = parse(input).toResult()
}

inline fun <T> buildSchema(
    crossinline block: ValidationScope.(T) -> T
): Schema<T> = Schema { input ->
    val scope = ValidationScope()
    val transformed = scope.block(input)
    if (scope.errors.isEmpty()) {
        ValidationResult.Valid(transformed)
    } else {
        ValidationResult.Invalid(scope.errors)
    }
}
