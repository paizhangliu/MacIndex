package com.macindex.macindex.userstate

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * The sole synchronous source for appearance. Appearance is intentionally absent from AppState:
 * AppCompat needs this value before asynchronous DataStore startup and before inflating content.
 */
class ThemeBootstrapStore private constructor(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
    )

    fun read(): Appearance {
        val stored = try {
            preferences.getInt(KEY_APPEARANCE, AppearanceCodec.FOLLOW_SYSTEM)
        } catch (failure: ClassCastException) {
            Log.w("ThemeBootstrap", "Ignoring an invalid appearance preference.", failure)
            AppearanceCodec.FOLLOW_SYSTEM
        }
        if (!AppearanceCodec.isKnown(stored)) {
            Log.w("ThemeBootstrap", "Ignoring unknown appearance value $stored.")
        }
        return AppearanceCodec.decode(stored)
    }

    @Throws(UserStateUnavailableException::class)
    fun write(appearance: Appearance) {
        if (!preferences.edit().putInt(KEY_APPEARANCE, AppearanceCodec.encode(appearance)).commit()) {
            throw UserStateUnavailableException("Unable to save appearance")
        }
    }

    companion object {
        private const val FILE_NAME = "macindex_theme_bootstrap"
        private const val KEY_APPEARANCE = "appearance"
    }
}

internal object AppearanceCodec {
    const val FOLLOW_SYSTEM = 0
    private const val LIGHT = 1
    private const val DARK = 2

    fun encode(appearance: Appearance): Int = when (appearance) {
        Appearance.FOLLOW_SYSTEM -> FOLLOW_SYSTEM
        Appearance.LIGHT -> LIGHT
        Appearance.DARK -> DARK
    }

    fun isKnown(stored: Int): Boolean = stored == FOLLOW_SYSTEM || stored == LIGHT || stored == DARK

    fun decode(stored: Int): Appearance = when (stored) {
        LIGHT -> Appearance.LIGHT
        DARK -> Appearance.DARK
        else -> Appearance.FOLLOW_SYSTEM
    }
}
