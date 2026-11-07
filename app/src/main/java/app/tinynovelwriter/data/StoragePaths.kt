package app.tinynovelwriter.data

/**
 * Turns a folder chosen in Android's folder picker into a plain file path, so the app
 * can keep using All files access instead of the picker's content URIs, and describes
 * paths for display.
 */
object StoragePaths {

    /** The picker's provider for phone storage and SD cards. */
    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * [treeDocumentId] is `DocumentsContract.getTreeDocumentId` of the picked URI, e.g.
     * `primary:Documents/NovelWriter` or `1A2B-3C4D:Novels`. [primaryRoot] is the phone
     * storage's path (`/storage/emulated/0`). Returns null for folders that aren't on
     * the phone or an SD card, such as cloud locations offered in the picker.
     */
    fun treePath(authority: String?, treeDocumentId: String?, primaryRoot: String): String? {
        if (authority != EXTERNAL_STORAGE_AUTHORITY || treeDocumentId == null) return null
        val colon = treeDocumentId.indexOf(':')
        if (colon <= 0) return null
        val volume = treeDocumentId.substring(0, colon)
        val relative = treeDocumentId.substring(colon + 1).trim('/')
        val root = when {
            volume == "primary" -> primaryRoot
            // The picker's "Documents" shortcut on the phone storage.
            volume == "home" -> "$primaryRoot/Documents"
            VOLUME_ID.matches(volume) -> "/storage/$volume"
            else -> return null
        }
        return if (relative.isEmpty()) root else "$root/$relative"
    }

    enum class Volume { INTERNAL, REMOVABLE, OTHER }

    /** A path split into its storage and the folders below it, for display. */
    data class Described(val volume: Volume, val folders: List<String>)

    fun describe(path: String, primaryRoot: String): Described {
        val clean = path.trimEnd('/')
        fun below(root: String) = clean.removePrefix(root).split('/').filter { it.isNotEmpty() }
        return when {
            clean == primaryRoot || clean.startsWith("$primaryRoot/") -> Described(Volume.INTERNAL, below(primaryRoot))
            REMOVABLE.find(clean) != null -> Described(Volume.REMOVABLE, below(REMOVABLE.find(clean)!!.value))
            else -> Described(Volume.OTHER, clean.split('/').filter { it.isNotEmpty() })
        }
    }

    /** SD card volume ids look like `1A2B-3C4D`. */
    private val VOLUME_ID = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
    private val REMOVABLE = Regex("^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
}
