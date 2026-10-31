package app.tinynovelwriter.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Starts a FolderSync sync through one of its automation deep links. FolderSync 4.x has
 * no broadcast for this; instead each install has its own links, shown on a folderpair's
 * Automation tab once automation is enabled in FolderSync's settings. The user pastes
 * the folderpair's "Sync" link into the app. There is no way to learn whether the sync
 * succeeded.
 */
object FolderSync {

    private val PACKAGES = listOf("dk.tacit.android.foldersync.full", "dk.tacit.android.foldersync.lite")
    private val LINK = Regex("""https://(www\.)?tacit\.dk/app/foldersync/trigger\S*""")

    fun isValidLink(link: String): Boolean = LINK.matches(link.trim())

    /** Returns null on success, or a message saying why the sync couldn't be started. */
    fun startSync(context: Context, link: String): String? {
        if (!isValidLink(link)) return "The FolderSync link isn't set up (Settings)."
        val pkg = PACKAGES.firstOrNull { isInstalled(context, it) } ?: return "FolderSync isn't installed."
        // Pinned to FolderSync, so the https link never opens in a browser. In a separate,
        // throwaway task: FolderSync's trigger screen sends its task to the back when done,
        // which would otherwise take this app to the home screen, or (in FolderSync's main
        // task) flash FolderSync's own screen on the way back.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.trim()))
            .setPackage(pkg)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        return try {
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "FolderSync didn't accept the link."
        }
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
