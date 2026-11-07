package com.blackout.gallery.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android asks "Allow Gallery to change/delete these?" before an app edits photos it didn't take.
 * With "Allow changes without asking" turned on in Settings, it skips the question.
 */
object MediaAccess {
    /** Set by the screen: shows Android's question and returns whether you tapped Allow. */
    var asker: (suspend (IntentSender) -> Boolean)? = null

    fun canSkipAsking(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)

    private suspend fun ask(sender: IntentSender): Boolean =
        withContext(Dispatchers.Main) { asker?.invoke(sender) ?: false }

    suspend fun allowWrite(context: Context, uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || canSkipAsking(context)) return true
        return ask(MediaStore.createWriteRequest(context.contentResolver, uris).intentSender)
    }

    /** Deletes the photos from the phone. Returns false if you said no. */
    suspend fun delete(context: Context, uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return true
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !canSkipAsking(context)) {
            return ask(MediaStore.createDeleteRequest(resolver, uris).intentSender)
        }
        for (uri in uris) {
            try {
                withContext(Dispatchers.IO) { resolver.delete(uri, null, null) }
            } catch (e: RecoverableSecurityException) {
                if (!ask(e.userAction.actionIntent.intentSender)) return false
                withContext(Dispatchers.IO) { resolver.delete(uri, null, null) }
            }
        }
        return true
    }
}
