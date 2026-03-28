package dev.chungjungsoo.gptmobile.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

object FileUtils {
    private const val TAG = "FileUtils"
    private const val MAX_IMAGE_UPLOAD_DIMENSION = 2048
    private const val IMAGE_UPLOAD_QUALITY = 85

    data class EncodedImage(
        val mimeType: String,
        val base64Data: String
    )

    /**
     * Read file from URI and encode it to base64 for upload.
     *
     * Image inputs may be downsampled and recompressed before encoding to reduce
     * memory usage during request construction.
     *
     * @param context Android context for ContentResolver
     * @param uriString File URI as string (content://, file://, or absolute path)
     * @return Base64-encoded upload payload, or null if error
     */
    fun readAndEncodeFile(context: Context, uriString: String): String? = try {
        readAndEncodeImageForUpload(context, uriString)?.base64Data ?: encodeFileToBase64(context, uriString)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to encode file for upload: $uriString", e)
        null
    }

    /**
     * Read an image from URI and encode it to base64 for upload.
     *
     * Animated/vector formats that should not be recompressed are streamed as-is.
     * Other image formats may be downsampled and recompressed to lower memory use.
     */
    fun readAndEncodeImageForUpload(context: Context, uriString: String): EncodedImage? = try {
        val mimeType = getMimeType(context, uriString)
        readAndEncodeImageForUpload(context, uriString, mimeType)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to encode image for upload: $uriString", e)
        null
    }

    internal fun readAndEncodeImageForUpload(context: Context, uriString: String, mimeType: String): EncodedImage? {
        if (!isImage(mimeType)) return null

        return when (mimeType) {
            "image/gif", "image/svg+xml" -> {
                encodeFileToBase64(context, uriString)?.let { base64 ->
                    EncodedImage(mimeType = mimeType, base64Data = base64)
                }
            }

            else -> {
                readAndEncodeScaledImage(context, uriString, mimeType)
                    ?: encodeFileToBase64(context, uriString)?.let { base64 ->
                        EncodedImage(mimeType = mimeType, base64Data = base64)
                    }
            }
        }
    }

    /**
     * Get InputStream from URI string
     * @param context Android context for ContentResolver
     * @param uriString File URI as string
     * @return InputStream or null if error
     */
    private fun getInputStreamFromUri(context: Context, uriString: String): InputStream? = try {
        when {
            uriString.startsWith("content://") -> {
                val uri = Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)
            }

            uriString.startsWith("file://") -> {
                val path = uriString.removePrefix("file://")
                FileInputStream(File(path))
            }

            else -> {
                // Assume it's an absolute path
                FileInputStream(File(uriString))
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open input stream for: $uriString", e)
        null
    }

    /**
     * Get MIME type from URI
     * @param context Android context for ContentResolver
     * @param uriString File URI as string
     * @return MIME type string, or "application/octet-stream" if unknown
     */
    fun getMimeType(context: Context, uriString: String): String = try {
        when {
            uriString.startsWith("content://") -> {
                val uri = Uri.parse(uriString)
                context.contentResolver.getType(uri) ?: getMimeTypeFromExtension(uriString)
            }

            else -> {
                getMimeTypeFromExtension(uriString)
            }
        }
    } catch (e: Exception) {
        "application/octet-stream"
    }

    /**
     * Get MIME type from file extension
     * @param filename File name or path
     * @return MIME type string, or "application/octet-stream" if unknown
     */
    private fun getMimeTypeFromExtension(filename: String): String = when (val extension = filename.substringAfterLast('.', "").lowercase()) {
        // Images
        "jpg", "jpeg" -> "image/jpeg"

        "png" -> "image/png"

        "gif" -> "image/gif"

        "bmp" -> "image/bmp"

        "webp" -> "image/webp"

        "tiff", "tif" -> "image/tiff"

        "svg" -> "image/svg+xml"

        // Documents
        "pdf" -> "application/pdf"

        "txt" -> "text/plain"

        "doc" -> "application/msword"

        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        "xls" -> "application/vnd.ms-excel"

        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    /**
     * Check if file is an image based on MIME type
     * @param mimeType MIME type string
     * @return true if image, false otherwise
     */
    fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/")

    /**
     * Check if file is a document based on MIME type
     * @param mimeType MIME type string
     * @return true if document, false otherwise
     */
    fun isDocument(mimeType: String): Boolean = mimeType in listOf(
        "application/pdf",
        "text/plain",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )

    /**
     * Get file size in bytes
     * @param context Android context for ContentResolver
     * @param uriString File URI as string
     * @return File size in bytes, or -1 if error
     */
    fun getFileSize(context: Context, uriString: String): Long = try {
        when {
            uriString.startsWith("content://") -> {
                val uri = Uri.parse(uriString)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                    fd.statSize
                } ?: -1L
            }

            uriString.startsWith("file://") -> {
                val path = uriString.removePrefix("file://")
                File(path).length()
            }

            else -> {
                File(uriString).length()
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get file size for: $uriString", e)
        -1L
    }

    /**
     * Validate file size
     * @param context Android context for ContentResolver
     * @param uriString File URI as string
     * @param maxSizeBytes Maximum allowed size in bytes (default 5MB)
     * @return true if file size is within limit, false otherwise
     */
    fun validateFileSize(context: Context, uriString: String, maxSizeBytes: Long = 5 * 1024 * 1024): Boolean {
        val size = getFileSize(context, uriString)
        return size in 1..maxSizeBytes
    }

    internal fun calculateImageInSampleSize(
        originalWidth: Int,
        originalHeight: Int,
        maxDimension: Int = MAX_IMAGE_UPLOAD_DIMENSION
    ): Int {
        if (originalWidth <= 0 || originalHeight <= 0 || maxDimension <= 0) return 1

        val longestEdge = maxOf(originalWidth, originalHeight)
        var sampleSize = 1

        while (longestEdge / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        return sampleSize
    }

    internal fun shouldPreserveAlpha(mimeType: String): Boolean = mimeType.contains("png") || mimeType.contains("webp")

    internal fun encodeToBase64(writeBytes: (OutputStream) -> Boolean): String? {
        val outputStream = ByteArrayOutputStream()
        val success = Base64.getEncoder().wrap(outputStream).use { base64Stream ->
            writeBytes(base64Stream)
        }

        return if (success) {
            outputStream.toString(Charsets.UTF_8.name())
        } else {
            null
        }
    }

    private fun readAndEncodeScaledImage(context: Context, uriString: String, mimeType: String): EncodedImage? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        getInputStreamFromUri(context, uriString)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val (compressFormat, uploadMimeType) = resolveImageCompressFormat(mimeType)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateImageInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight)
            inPreferredConfig = if (shouldPreserveAlpha(uploadMimeType)) {
                Bitmap.Config.ARGB_8888
            } else {
                Bitmap.Config.RGB_565
            }
        }

        val bitmap = getInputStreamFromUri(context, uriString)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        return try {
            val base64Data = encodeToBase64 { outputStream ->
                bitmap.compress(compressFormat, IMAGE_UPLOAD_QUALITY, outputStream)
            } ?: return null

            EncodedImage(
                mimeType = uploadMimeType,
                base64Data = base64Data
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun encodeFileToBase64(context: Context, uriString: String): String? {
        val outputStream = ByteArrayOutputStream()
        getInputStreamFromUri(context, uriString)?.use { inputStream ->
            Base64.getEncoder().wrap(outputStream).use { base64Stream ->
                inputStream.copyTo(base64Stream)
            }
        } ?: return null

        return outputStream.toString(Charsets.UTF_8.name())
    }

    private fun resolveImageCompressFormat(mimeType: String): Pair<Bitmap.CompressFormat, String> = when {
        mimeType.contains("png") -> Bitmap.CompressFormat.PNG to "image/png"
        mimeType.contains("webp") -> Bitmap.CompressFormat.WEBP_LOSSY to "image/webp"
        else -> Bitmap.CompressFormat.JPEG to "image/jpeg"
    }
}
