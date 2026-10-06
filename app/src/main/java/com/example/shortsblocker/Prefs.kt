package com.example.shortsblocker

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Prefs {
    private const val FILE = "blocker"
    private const val K_HASH = "pw_hash"
    private const val K_SALT = "pw_salt"
    private const val K_PAUSE_UNTIL = "pause_until_elapsed"
    private const val K_FAILS = "fails"
    private const val K_LOCK_UNTIL = "lock_until_elapsed"

    const val PAUSE_MS = 5 * 60 * 1000L
    const val MIN_LENGTH = 16

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun hasPassword(c: Context) = p(c).contains(K_HASH)

    /** Returns null if the password is acceptable, otherwise a message explaining why not. */
    fun validateStrength(pw: String): String? {
        if (pw.length < MIN_LENGTH) return "Use at least $MIN_LENGTH characters."
        if (pw.none { it.isUpperCase() }) return "Add an uppercase letter."
        if (pw.none { it.isLowerCase() }) return "Add a lowercase letter."
        if (pw.none { it.isDigit() }) return "Add a digit."
        if (pw.all { it.isLetterOrDigit() }) return "Add a symbol such as ! @ # $ %."
        return null
    }

    private fun hash(pw: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pw.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun setPassword(c: Context, pw: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p(c).edit()
            .putString(K_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(K_HASH, Base64.encodeToString(hash(pw, salt), Base64.NO_WRAP))
            .apply()
    }

    fun lockedForMs(c: Context): Long {
        val left = p(c).getLong(K_LOCK_UNTIL, 0L) - SystemClock.elapsedRealtime()
        return if (left in 1..10 * 60 * 1000L) left else 0L
    }

    /** Checks the password. Repeated failures cause a growing lockout. */
    fun checkPassword(c: Context, pw: String): Boolean {
        if (lockedForMs(c) > 0) return false
        val sp = p(c)
        val salt = Base64.decode(sp.getString(K_SALT, "") ?: return false, Base64.NO_WRAP)
        val expected = Base64.decode(sp.getString(K_HASH, "") ?: return false, Base64.NO_WRAP)
        val actual = hash(pw, salt)
        val ok = java.security.MessageDigest.isEqual(expected, actual)
        if (ok) {
            sp.edit().putInt(K_FAILS, 0).apply()
        } else {
            val fails = sp.getInt(K_FAILS, 0) + 1
            val lock = if (fails >= 3) 30_000L * (fails - 2) else 0L
            sp.edit()
                .putInt(K_FAILS, fails)
                .putLong(K_LOCK_UNTIL, SystemClock.elapsedRealtime() + lock)
                .apply()
        }
        return ok
    }

    fun startPause(c: Context) {
        p(c).edit().putLong(K_PAUSE_UNTIL, SystemClock.elapsedRealtime() + PAUSE_MS).apply()
    }

    fun endPause(c: Context) {
        p(c).edit().putLong(K_PAUSE_UNTIL, 0L).apply()
    }

    fun pauseLeftMs(c: Context): Long {
        val left = p(c).getLong(K_PAUSE_UNTIL, 0L) - SystemClock.elapsedRealtime()
        // Values larger than the pause length mean the phone rebooted; treat as expired.
        return if (left in 1..PAUSE_MS) left else 0L
    }

    fun isPaused(c: Context) = pauseLeftMs(c) > 0
}
