package com.example.repartija.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

object ImageUtils {
    /**
     * Resizes and compresses an image to fit within specified constraints.
     * @param bytes The original image bytes.
     * @param maxWidth Maximum width in pixels.
     * @param maxHeight Maximum height in pixels.
     * @param maxSizeBytes Maximum size in bytes.
     * @return The processed image bytes.
     */
    fun compressAndResizeImage(
        bytes: ByteArray,
        maxWidth: Int = 512,
        maxHeight: Int = 512,
        maxSizeBytes: Long = 500 * 1024 // 500KB
    ): ByteArray {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        // Calculate inSampleSize
        var inSampleSize = 1
        if (options.outHeight > maxHeight || options.outWidth > maxWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while (halfHeight / inSampleSize >= maxHeight && halfWidth / inSampleSize >= maxWidth) {
                inSampleSize *= 2
            }
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return bytes

        // Precise Resize
        if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
            val scale = Math.min(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
            val matrix = Matrix()
            matrix.postScale(scale, scale)
            val resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (resizedBitmap != bitmap) {
                bitmap.recycle()
                bitmap = resizedBitmap
            }
        }

        // Compression
        var quality = 90
        var outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        
        while (outputStream.toByteArray().size > maxSizeBytes && quality > 10) {
            quality -= 10
            outputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }
        
        val result = outputStream.toByteArray()
        bitmap.recycle()
        return result
    }
}
