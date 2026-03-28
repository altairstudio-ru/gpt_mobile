package dev.chungjungsoo.gptmobile.util

import org.junit.Assert.assertEquals
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
}
