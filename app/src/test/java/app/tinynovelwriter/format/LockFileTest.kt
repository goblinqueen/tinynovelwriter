package app.tinynovelwriter.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockFileTest {

    @Test
    fun parsesNovelWriterLock() {
        assertEquals(
            LockInfo("MOTHERSHIP", "win32", "10.0.26200", 1793309900L),
            LockFile.parse("MOTHERSHIP;win32;10.0.26200;1793309900\n"),
        )
    }

    @Test
    fun toleratesBrokenLock() {
        val info = LockFile.parse("garbage")
        assertEquals("garbage", info.host)
        assertEquals("", info.os)
        assertNull(info.lockedAt)
    }
}
