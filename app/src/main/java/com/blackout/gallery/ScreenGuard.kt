package com.blackout.gallery

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

/** Screen protection: blanks the app in the app switcher and blocks screenshots and screen recording. On by default. */
object ScreenGuard {
    private const val PREFS = "blackout_ui"
    private const val KEY = "screen_protection"

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    /** Saves the choice and applies it to the screen that is showing now. */
    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) { apply(c); return }
            c = c.baseContext
        }
    }

    fun apply(activity: Activity) {
        if (isOn(activity)) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
