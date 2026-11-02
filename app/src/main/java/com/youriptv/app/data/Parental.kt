package com.youriptv.app.data

import java.security.MessageDigest
import java.security.SecureRandom

/** Minimal string key-value storage so the PIN logic can be unit-tested without Android. A null value removes the key. */
interface StringStore {
    fun get(key: String): String?
    fun put(values: Map<String, String?>)
}

/** The two independent protections. Each has its own hash, salt, and failed-attempt counter. */
enum class PinKind(val key: String, val label: String) {
    PARENTAL("pin", "parental PIN"),
    SETTINGS("settings_pin", "Settings PIN");

    val other: PinKind get() = if (this == PARENTAL) SETTINGS else PARENTAL
}

sealed class PinCheck {
    object Ok : PinCheck()
    data class Wrong(val attemptsLeft: Int) : PinCheck()
    data class LockedOut(val remainingMs: Long) : PinCheck()
}

/**
 * Stores the parental PIN and the Settings PIN as salted SHA-256 hashes (a PIN itself is never stored).
 * The two PINs must differ, so one can never unlock the other protection.
 * After [MAX_ATTEMPTS] wrong entries a PIN is refused for an increasing time; the counter is persisted so restarting the app does not reset it.
 */
class PinVault(private val store: StringStore, private val clock: () -> Long = System::currentTimeMillis) {

    fun has(kind: PinKind) = store.get(hashKey(kind)) != null

    /** Returns an error message, or null when [pin] may be saved as the new [kind] PIN. */
    fun validateNew(kind: PinKind, pin: String): String? = when {
        !pin.matches(PIN_FORMAT) -> "The PIN must be 4 to 8 digits"
        matches(kind.other, pin) -> "Choose a PIN that is different from the ${kind.other.label}"
        else -> null
    }

    /**
     * Like [validateNew], for interactive entry. Because rejecting a PIN equal to the other one reveals that PIN,
     * every candidate counts as an attempt against the other PIN and obeys its lockout, so this cannot be used to guess it faster.
     */
    fun screenNew(kind: PinKind, pin: String): String? {
        if (!pin.matches(PIN_FORMAT)) return "The PIN must be 4 to 8 digits"
        val other = kind.other
        if (!has(other)) return null
        lockoutRemaining(other).let { if (it > 0) return "Too many attempts. Try again in ${(it + 999) / 1000} s" }
        val fails = failures(other) + 1
        val values = HashMap<String, String?>()
        values[failKey(other)] = fails.toString()
        if (fails >= MAX_ATTEMPTS) values[untilKey(other)] = (clock() + lockoutMs(fails)).toString()
        store.put(values)
        return if (matches(other, pin)) "Choose a PIN that is different from the ${other.label}" else null
    }

    /** Saves a new [kind] PIN (null removes it). Throws if [pin] breaks [validateNew]. Never touches the other PIN. */
    fun set(kind: PinKind, pin: String?) {
        if (pin.isNullOrEmpty()) {
            store.put(mapOf(hashKey(kind) to null, saltKey(kind) to null, failKey(kind) to null, untilKey(kind) to null))
            return
        }
        validateNew(kind, pin)?.let { throw IllegalArgumentException(it) }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        store.put(mapOf(saltKey(kind) to salt, hashKey(kind) to hash(pin, salt), failKey(kind) to null, untilKey(kind) to null))
    }

    /** Checks [pin] against the [kind] PIN, counting failures. A [kind] with no PIN set always passes. */
    fun check(kind: PinKind, pin: String): PinCheck {
        if (!has(kind)) return PinCheck.Ok
        lockoutRemaining(kind).let { if (it > 0) return PinCheck.LockedOut(it) }
        if (matches(kind, pin)) {
            store.put(mapOf(failKey(kind) to null, untilKey(kind) to null))
            return PinCheck.Ok
        }
        val fails = failures(kind) + 1
        val values = HashMap<String, String?>()
        values[failKey(kind)] = fails.toString()
        if (fails >= MAX_ATTEMPTS) {
            val ms = lockoutMs(fails)
            values[untilKey(kind)] = (clock() + ms).toString()
            store.put(values)
            return PinCheck.LockedOut(ms)
        }
        store.put(values)
        return PinCheck.Wrong(MAX_ATTEMPTS - fails)
    }

    fun lockoutRemaining(kind: PinKind): Long =
        ((store.get(untilKey(kind))?.toLongOrNull() ?: 0L) - clock()).coerceAtLeast(0L)

    fun failures(kind: PinKind) = store.get(failKey(kind))?.toIntOrNull() ?: 0

    private fun matches(kind: PinKind, pin: String): Boolean {
        val h = store.get(hashKey(kind)) ?: return false
        return hash(pin, store.get(saltKey(kind)).orEmpty()) == h
    }

    private fun hashKey(k: PinKind) = "${k.key}_hash"
    private fun saltKey(k: PinKind) = "${k.key}_salt"
    private fun failKey(k: PinKind) = "${k.key}_fails"
    private fun untilKey(k: PinKind) = "${k.key}_locked_until"

    companion object {
        const val MAX_ATTEMPTS = 5
        private const val BASE_LOCKOUT_MS = 30_000L
        private const val MAX_LOCKOUT_MS = 15 * 60_000L
        private val PIN_FORMAT = Regex("\\d{4,8}")

        /** 30 s after the 5th wrong entry, doubling with each further wrong entry, up to 15 min. */
        fun lockoutMs(failures: Int): Long {
            val extra = (failures - MAX_ATTEMPTS).coerceIn(0, 10)
            return (BASE_LOCKOUT_MS shl extra).coerceAtMost(MAX_LOCKOUT_MS)
        }

        fun hash(pin: String, salt: String): String =
            MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).toHex()

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}

/** Pure group rules shared by every screen that lists or plays content. */
object ParentalRules {
    /** Locks need a parental PIN, so removing the PIN can never lock the user out. */
    fun isLocked(hasPin: Boolean, group: String?, lockedGroups: Set<String>, blockAdult: Boolean): Boolean =
        hasPin && group != null && (group in lockedGroups || (blockAdult && AdultGroups.isAdult(group)))

    fun isAdultBlocked(hasPin: Boolean, group: String?, blockAdult: Boolean) = hasPin && blockAdult && AdultGroups.isAdult(group)

    /** Blocked adult groups are always hidden; other locked groups only when "Hide locked groups" is on. */
    fun isHidden(hasPin: Boolean, group: String?, lockedGroups: Set<String>, blockAdult: Boolean, hideLocked: Boolean) =
        isAdultBlocked(hasPin, group, blockAdult) || (hideLocked && isLocked(hasPin, group, lockedGroups, blockAdult))

    /**
     * Builds before the separate Settings PIN locked Settings with the parental PIN ("lock_settings").
     * Such an install keeps Settings locked until the user, after entering that old PIN once, creates a separate Settings PIN
     * or turns the lock off. Without a parental PIN the old lock never applied, so it is simply dropped.
     */
    fun legacySettingsLockPending(legacyFlag: Boolean, hasSettingsPin: Boolean, hasParentalPin: Boolean) =
        legacyFlag && !hasSettingsPin && hasParentalPin
}
