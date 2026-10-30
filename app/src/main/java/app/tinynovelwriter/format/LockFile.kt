package app.tinynovelwriter.format

/** Contents of `nwProject.lock`: `host;os;kernel;unixtime`, written by novelWriter on open. */
data class LockInfo(
    val host: String,
    val os: String,
    val kernel: String,
    /** Seconds since the epoch, or null if the field isn't a number. */
    val lockedAt: Long?,
)

object LockFile {
    const val NAME = "nwProject.lock"

    fun parse(text: String): LockInfo {
        val parts = text.trim().split(";")
        return LockInfo(
            host = parts.getOrElse(0) { "" },
            os = parts.getOrElse(1) { "" },
            kernel = parts.getOrElse(2) { "" },
            lockedAt = parts.getOrNull(3)?.trim()?.toLongOrNull(),
        )
    }
}
