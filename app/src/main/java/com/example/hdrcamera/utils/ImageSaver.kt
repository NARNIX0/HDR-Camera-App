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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageSaver {
    private const val TAG = "ImageSaver"
    private const val BASE_PATH = "Pictures/HDRCameraAppOutput"
    private const val MIME_TYPE = "image/jpeg"
    private const val QUALITY = 95
    
    // Keep track of current session timestamp for batching
    private var currentBatchTimestamp = generateTimestamp()
    
    /**
     * Generates a new batch timestamp
     */
    fun generateNewBatch() {
        currentBatchTimestamp = generateTimestamp()
        Log.d(TAG, "Generated new batch timestamp: $currentBatchTimestamp")
    }
    
    /**
     * Get current relative path with batch folder
     */
    private fun getCurrentPath(): String {
        return "$BASE_PATH/Batch_$currentBatchTimestamp"
    }
    
    /**
     * Generates a timestamp string for folder naming
     */
    private fun generateTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

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
            val relativePath = getCurrentPath()
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            Log.d(TAG, "Attempting to save image with name: $displayName in $relativePath")

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
    
    /**
     * Saves a merged HDR bitmap to the device gallery using MediaStore API
     * This is specialized for merged HDR images and adds appropriate metadata
     * 
     * @param context Application context
     * @param bitmap The merged HDR bitmap to save
     * @param displayName Filename to use for the saved image
     * @return true if saved successfully, false otherwise
     */
    suspend fun saveHDRMergedImage(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val relativePath = getCurrentPath()
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
                put(MediaStore.Images.Media.TITLE, "HDR Merged Image")
                put(MediaStore.Images.Media.DESCRIPTION, "Created with HDR Camera App")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            Log.d(TAG, "Attempting to save HDR merged image: $displayName in $relativePath")

            val uri: Uri? = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            Log.d(TAG, "MediaStore insert returned URI: $uri")

            if (uri != null) {
                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    Log.d(TAG, "Got output stream, compressing HDR bitmap...")
                    // Use higher quality for HDR merged images
                    val saved = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    outputStream.close()
                    
                    // If we're on Android 10 (Q) or higher, we need to update IS_PENDING flag
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, contentValues, null, null)
                        Log.d(TAG, "Updated IS_PENDING flag to 0")
                    }
                    
                    Log.d(TAG, "HDR merged image saved successfully: $displayName, URI: $uri")
                    return@withContext saved
                } else {
                    Log.e(TAG, "Failed to get output stream for uri: $uri")
                }
            } else {
                Log.e(TAG, "Failed to get URI from MediaStore insert")
            }
            
            return@withContext false
        } catch (e: IOException) {
            Log.e(TAG, "IOException saving HDR merged image: ${e.message}", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error saving HDR merged image: ${e.message}", e)
            return@withContext false
        }
    }
} 