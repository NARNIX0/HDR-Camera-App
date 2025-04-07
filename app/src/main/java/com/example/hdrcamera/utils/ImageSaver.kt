package com.example.hdrcamera.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

object ImageSaver {
    private const val TAG = "ImageSaver"
    private const val RELATIVE_PATH = "Pictures/HDRCamera"
    private const val MIME_TYPE = "image/jpeg"
    private const val QUALITY = 95

    /**
     * Saves a bitmap to the device gallery using MediaStore API
     * 
     * @param context Application context
     * @param bitmap The bitmap to save
     * @param displayName Filename to use for the saved image
     * @return true if saved successfully, false otherwise
     */
    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            Log.d(TAG, "Attempting to save image with name: $displayName in $RELATIVE_PATH")

            val uri: Uri? = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            Log.d(TAG, "MediaStore insert returned URI: $uri")

            if (uri != null) {
                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    Log.d(TAG, "Got output stream, compressing bitmap...")
                    val saved = bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, outputStream)
                    outputStream.close()
                    
                    // If we're on Android 10 (Q) or higher, we need to update IS_PENDING flag
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, contentValues, null, null)
                        Log.d(TAG, "Updated IS_PENDING flag to 0")
                    }
                    
                    Log.d(TAG, "Image saved successfully to gallery: $displayName, URI: $uri, compression result: $saved")
                    return@withContext saved
                } else {
                    Log.e(TAG, "Failed to get output stream for uri: $uri")
                }
            } else {
                Log.e(TAG, "Failed to get URI from MediaStore insert")
            }
            
            Log.e(TAG, "Failed to get output stream for uri: $uri")
            return@withContext false
        } catch (e: IOException) {
            Log.e(TAG, "IOException saving image to gallery: ${e.message}", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error saving image to gallery: ${e.message}", e)
            return@withContext false
        }
    }
} 