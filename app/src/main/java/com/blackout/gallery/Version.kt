package com.blackout.gallery

object Version {
    val full: String get() = "Gallery ${BuildConfig.VERSION_NAME} \u00B7 build ${BuildConfig.BUILD_NUMBER} \u00B7 ${BuildConfig.GIT_SHA}"
    val tag: String get() = "build ${BuildConfig.BUILD_NUMBER}"
    const val RELEASES = "https://github.com/Blackout-78/Gallery/releases"
    fun openReleases(c: android.content.Context) {
        runCatching {
            c.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(RELEASES))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
