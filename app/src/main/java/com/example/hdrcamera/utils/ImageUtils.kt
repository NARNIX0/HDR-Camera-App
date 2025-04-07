package com.example.hdrcamera.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ImageUtils"
private const val HDR_IMAGE_PREFIX = "HDR_IMG_"
private const val HDR_OUTPUT_PREFIX = "HDR_Output_"

object ImageUtils {
    /**
     * Save a bitmap to the gallery
     * @return The Uri of the saved image or null if saving failed
     */
    fun saveImageToGallery(context: Context, bitmap: Bitmap): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "${HDR_IMAGE_PREFIX}${timestamp}.jpg"
        
        Log.d(TAG, "Saving image with filename: $filename")
        
        var uri: Uri? = null
        var outputStream: OutputStream? = null
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10 and above, use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HDRCamera")
                }
                
                val contentResolver: ContentResolver = context.contentResolver
                uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                
                uri?.let {
                    Log.d(TAG, "Created MediaStore entry with URI: $it")
                    outputStream = contentResolver.openOutputStream(it)
                    outputStream?.let { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        Log.d(TAG, "Image compressed and written to OutputStream")
                    }
                }
            } else {
                // For devices before Android 10
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/HDRCamera"
                val directory = File(imagesDir)
                if (!directory.exists()) {
                    val success = directory.mkdirs()
                    Log.d(TAG, "Created directory $imagesDir: $success")
                }
                
                val file = File(directory, filename)
                Log.d(TAG, "Saving image to file: ${file.absolutePath}")
                val fileOutputStream = FileOutputStream(file)
                outputStream = fileOutputStream
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
                Log.d(TAG, "Image compressed and written to FileOutputStream")
                
                // Notify gallery and get content URI
                var contentUri: Uri? = null
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.toString()),
                    arrayOf("image/jpeg")
                ) { path, uri ->
                    Log.d(TAG, "Media scanner completed. Path: $path, URI: $uri")
                    contentUri = uri
                }
                
                // Wait a moment for the MediaScanner to process
                var waitCount = 0
                while (contentUri == null && waitCount < 10) {
                    Thread.sleep(100)
                    waitCount++
                }
                
                // Use the content URI if available, otherwise fallback to file URI
                uri = contentUri ?: Uri.fromFile(file)
                Log.d(TAG, "Final URI after MediaScanner: $uri")
            }
            
            Log.d(TAG, "Image saved successfully at $uri")
            return uri
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save image: ${e.message}", e)
            return null
        } finally {
            try {
                outputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing output stream", e)
            }
        }
    }
    
    /**
     * Get all HDR images saved by the app
     */
    fun getHDRImages(context: Context): List<Uri> {
        val images = mutableListOf<Uri>()
        val contentResolver = context.contentResolver
        
        try {
            Log.d(TAG, "Querying for HDR images with prefix: $HDR_IMAGE_PREFIX or $HDR_OUTPUT_PREFIX")
            
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            // Query for both types of our HDR images
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("${HDR_IMAGE_PREFIX}%", "${HDR_OUTPUT_PREFIX}%")
            
            // Sort by date, newest first
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            
            val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            Log.d(TAG, "Query URI: $queryUri")
            
            contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                Log.d(TAG, "Query returned ${cursor.count} results")
                
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    
                    val contentUri = ContentUris.withAppendedId(queryUri, id)
                    Log.d(TAG, "Found image: $name with URI: $contentUri")
                    images.add(contentUri)
                }
            }
            
            // If we found no images in MediaStore, check the file system directly as a fallback
            if (images.isEmpty() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                checkFileSystemForImages(context, images)
            }
            
            Log.d(TAG, "Returning ${images.size} images")
            return images
        } catch (e: Exception) {
            Log.e(TAG, "Error querying for images", e)
            return emptyList()
        }
    }
    
    /**
     * Fallback for older devices - check file system directly
     */
    private fun checkFileSystemForImages(context: Context, images: MutableList<Uri>) {
        try {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "HDRCamera"
            )
            
            Log.d(TAG, "Checking directory: ${directory.absolutePath}")
            
            if (!directory.exists() || !directory.isDirectory) {
                Log.d(TAG, "Directory doesn't exist or is not a directory")
                return
            }
            
            val imageFiles = directory.listFiles { file -> 
                file.isFile && (file.name.startsWith(HDR_IMAGE_PREFIX) || 
                                file.name.startsWith(HDR_OUTPUT_PREFIX) ||
                                file.name.toLowerCase(Locale.ROOT).endsWith(".jpg") || 
                                file.name.toLowerCase(Locale.ROOT).endsWith(".jpeg"))
            }
            
            Log.d(TAG, "Found ${imageFiles?.size ?: 0} files in directory")
            
            imageFiles?.forEach { file ->
                Log.d(TAG, "Found file: ${file.name} at ${file.absolutePath}")
                val uri = Uri.fromFile(file)
                images.add(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file system for images", e)
        }
    }

    /**
     * Alternative method that directly loads images from filesystem
     */
    fun getHDRImagesFromFileSystem(context: Context): List<Uri> {
        val images = mutableListOf<Uri>()
        
        try {
            // Get directory for HDR images
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "HDRCamera"
            )
            
            Log.d(TAG, "Looking for images in directory: ${directory.absolutePath}")
            
            if (!directory.exists() || !directory.isDirectory) {
                Log.d(TAG, "Directory doesn't exist or is not a directory")
                return emptyList()
            }
            
            // Find all image files in the directory
            val imageFiles = directory.listFiles { file -> 
                file.isFile && (file.name.startsWith(HDR_IMAGE_PREFIX) || 
                                file.name.startsWith(HDR_OUTPUT_PREFIX) ||
                                file.name.toLowerCase(Locale.ROOT).endsWith(".jpg") || 
                                file.name.toLowerCase(Locale.ROOT).endsWith(".jpeg"))
            }
            
            Log.d(TAG, "Found ${imageFiles?.size ?: 0} image files in directory")
            
            // Convert files to URIs
            imageFiles?.forEach { file ->
                try {
                    val uri: Uri
                    
                    // Use content URI if possible through MediaStore
                    val contentUri = getContentUriForFile(context, file)
                    if (contentUri != null) {
                        uri = contentUri
                        Log.d(TAG, "Using content URI for file: $uri")
                    } else {
                        // Fallback to file URI
                        uri = Uri.fromFile(file)
                        Log.d(TAG, "Using file URI: $uri for ${file.name}")
                    }
                    
                    images.add(uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding file ${file.name} to images list", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting images from file system", e)
        }
        
        return images
    }

    /**
     * Try to get a content URI for a file
     */
    private fun getContentUriForFile(context: Context, file: File): Uri? {
        try {
            // Query MediaStore for file
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = MediaStore.Images.Media.DATA + "=?"
            val selectionArgs = arrayOf(file.absolutePath)
            
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting content URI for file: ${file.absolutePath}", e)
        }
        
        return null
    }

    /**
     * Get the most recently captured HDR image as a File object
     */
    fun getMostRecentHDRImageFile(context: Context): File? {
        try {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "HDRCamera"
            )
            
            if (!directory.exists() || !directory.isDirectory) {
                Log.d(TAG, "Directory doesn't exist or is not a directory: ${directory.absolutePath}")
                return null
            }
            
            val imageFiles = directory.listFiles { file -> 
                file.isFile && (file.name.startsWith(HDR_IMAGE_PREFIX) || 
                                file.name.startsWith(HDR_OUTPUT_PREFIX) || 
                                file.name.toLowerCase(Locale.ROOT).endsWith(".jpg") || 
                                file.name.toLowerCase(Locale.ROOT).endsWith(".jpeg"))
            }
            
            if (imageFiles == null || imageFiles.isEmpty()) {
                Log.d(TAG, "No image files found in directory: ${directory.absolutePath}")
                return null
            }
            
            // Sort by last modified date, newest first
            val mostRecentFile = imageFiles.maxByOrNull { it.lastModified() }
            
            mostRecentFile?.let {
                Log.d(TAG, "Found most recent HDR image file: ${it.absolutePath}")
            } ?: run {
                Log.d(TAG, "Couldn't determine most recent file among ${imageFiles.size} files")
            }
            
            return mostRecentFile
        } catch (e: Exception) {
            Log.e(TAG, "Error getting most recent HDR image file", e)
            return null
        }
    }
} 