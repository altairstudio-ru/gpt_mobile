package dev.chungjungsoo.gptmobile.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUtilsTest {

    @Test
    fun `large images are downsampled until longest edge fits upload limit`() {
        assertEquals(2, FileUtils.calculateImageInSampleSize(4000, 3000, 2048))
        assertEquals(4, FileUtils.calculateImageInSampleSize(5000, 3750, 2048))
        assertEquals(8, FileUtils.calculateImageInSampleSize(9000, 6000, 2048))
    }

    @Test
    fun `images already within upload limit keep original size`() {
        assertEquals(1, FileUtils.calculateImageInSampleSize(2048, 1536, 2048))
        assertEquals(1, FileUtils.calculateImageInSampleSize(1600, 1200, 2048))
    }

    @Test
    fun `transparent upload formats preserve alpha capable bitmap config`() {
        assertTrue(FileUtils.shouldPreserveAlpha("image/png"))
        assertTrue(FileUtils.shouldPreserveAlpha("image/webp"))
        assertFalse(FileUtils.shouldPreserveAlpha("image/jpeg"))
    }

    @Test
    fun `streaming base64 encoder returns encoded data when writer succeeds`() {
        val encoded = FileUtils.encodeToBase64 { outputStream ->
            outputStream.write("hello".toByteArray())
            true
        }

        assertEquals("aGVsbG8=", encoded)
    }

    @Test
    fun `streaming base64 encoder returns null when writer fails`() {
        val encoded = FileUtils.encodeToBase64 { outputStream ->
            outputStream.write("ignored".toByteArray())
            false
        }

        assertNull(encoded)
    }
}
