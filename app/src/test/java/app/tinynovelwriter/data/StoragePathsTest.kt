package app.tinynovelwriter.data

import app.tinynovelwriter.data.StoragePaths.Described
import app.tinynovelwriter.data.StoragePaths.Volume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoragePathsTest {

    private val root = "/storage/emulated/0"
    private val ext = StoragePaths.EXTERNAL_STORAGE_AUTHORITY

    @Test
    fun phoneStorage() {
        assertEquals("$root/Documents/NovelWriter", StoragePaths.treePath(ext, "primary:Documents/NovelWriter", root))
        assertEquals(root, StoragePaths.treePath(ext, "primary:", root))
        assertEquals("$root/Documents/NovelWriter", StoragePaths.treePath(ext, "home:NovelWriter", root))
    }

    @Test
    fun sdCard() {
        assertEquals("/storage/1A2B-3C4D/Novels", StoragePaths.treePath(ext, "1A2B-3C4D:Novels", root))
    }

    @Test
    fun otherLocationsAreRefused() {
        assertNull(StoragePaths.treePath("com.google.android.apps.docs.storage", "abc:def", root))
        assertNull(StoragePaths.treePath(ext, "weird:thing", root))
        assertNull(StoragePaths.treePath(ext, "noColon", root))
        assertNull(StoragePaths.treePath(ext, null, root))
    }

    @Test
    fun describesPaths() {
        assertEquals(Described(Volume.INTERNAL, listOf("Documents", "NovelWriter")), StoragePaths.describe("$root/Documents/NovelWriter/", root))
        assertEquals(Described(Volume.INTERNAL, emptyList()), StoragePaths.describe(root, root))
        assertEquals(Described(Volume.REMOVABLE, listOf("Novels")), StoragePaths.describe("/storage/1A2B-3C4D/Novels", root))
        assertEquals(Described(Volume.OTHER, listOf("data", "x")), StoragePaths.describe("/data/x", root))
    }
}
