package com.example.hdrcamera.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

/**
 * Utility class for merging HDR images
 */
class HDRMergeUtils {
    companion object {
        private const val TAG = "HDRMergeUtils"
        private const val HDR_OUTPUT_PREFIX = "HDR_Output_"
        
        /**
         * Merge multiple images with different exposures into a single HDR image
         * 
         * @param context Application context
         * @param imageUris List of image URIs to merge, should be ordered from darkest to brightest
         * @return The merged HDR bitmap, or null if merging failed
         */
        suspend fun mergeHDRImages(
            context: Context,
            imageUris: List<Uri>
        ): Bitmap? = withContext(Dispatchers.IO) {
            try {
                if (imageUris.isEmpty() || imageUris.size < 2) {
                    Log.e(TAG, "Need at least 2 images to merge, received ${imageUris.size}")
                    return@withContext null
                }
                
                Log.d(TAG, "Starting HDR merge with ${imageUris.size} images")
                
                // Load all bitmaps
                val bitmaps = loadBitmaps(context, imageUris)
                if (bitmaps.isEmpty()) {
                    Log.e(TAG, "Failed to load any bitmaps")
                    return@withContext null
                }
                
                Log.d(TAG, "Loaded ${bitmaps.size} bitmaps for HDR merge")
                
                // Make sure all images are the same size
                val referenceWidth = bitmaps[0].width
                val referenceHeight = bitmaps[0].height
                
                val scaledBitmaps = ArrayList<Bitmap>(bitmaps.size)
                for (bitmap in bitmaps) {
                    if (bitmap.width != referenceWidth || bitmap.height != referenceHeight) {
                        Log.d(TAG, "Scaling bitmap from ${bitmap.width}x${bitmap.height} to ${referenceWidth}x${referenceHeight}")
                        scaledBitmaps.add(bitmap.scale(referenceWidth, referenceHeight))
                    } else {
                        scaledBitmaps.add(bitmap)
                    }
                }
                
                // Perform exposure fusion
                val result = exposureFusion(scaledBitmaps)
                
                // Clean up bitmaps to avoid memory leaks
                for (bitmap in scaledBitmaps) {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
                
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "Error merging HDR images: ${e.message}", e)
                return@withContext null
            }
        }
        
        /**
         * Generate a filename for an HDR output image
         */
        fun generateHDROutputFilename(): String {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            return "${HDR_OUTPUT_PREFIX}${timestamp}.jpg"
        }

        /**
         * Load bitmaps from a list of URIs
         */
        private suspend fun loadBitmaps(
            context: Context,
            uris: List<Uri>
        ): ArrayList<Bitmap> = withContext(Dispatchers.IO) {
            val bitmaps = ArrayList<Bitmap>()
            
            for (uri in uris) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        // First decode with inJustDecodeBounds=true to check dimensions
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                        
                        // Calculate inSampleSize
                        val MAX_DIMENSION = 2048 // Limit maximum size to avoid memory issues
                        var inSampleSize = 1
                        
                        if (options.outHeight > MAX_DIMENSION || options.outWidth > MAX_DIMENSION) {
                            val halfHeight = options.outHeight / 2
                            val halfWidth = options.outWidth / 2
                            
                            // Calculate inSampleSize
                            while ((halfHeight / inSampleSize) >= MAX_DIMENSION ||
                                   (halfWidth / inSampleSize) >= MAX_DIMENSION) {
                                inSampleSize *= 2
                            }
                        }
                        
                        // Decode with inSampleSize set
                        context.contentResolver.openInputStream(uri)?.use { newInputStream ->
                            val decodeOptions = BitmapFactory.Options().apply {
                                this.inSampleSize = inSampleSize
                            }
                            
                            val bitmap = BitmapFactory.decodeStream(newInputStream, null, decodeOptions)
                            if (bitmap != null) {
                                bitmaps.add(bitmap)
                                Log.d(TAG, "Loaded bitmap from $uri with size ${bitmap.width}x${bitmap.height}")
                            } else {
                                Log.e(TAG, "Failed to decode bitmap from $uri")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading bitmap from $uri: ${e.message}", e)
                }
            }
            
            return@withContext bitmaps
        }
        
        /**
         * Simple exposure fusion algorithm
         */
        private fun exposureFusion(images: ArrayList<Bitmap>): Bitmap? {
            if (images.isEmpty()) return null
            if (images.size == 1) return images[0]
            
            val width = images[0].width
            val height = images[0].height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            
            try {
                // For simplicity, we'll use a weighted average approach
                val weights = calculateWeights(images.size)
                Log.d(TAG, "Using weights for ${images.size} images")
                
                // Create a paint with blending mode
                val paint = Paint().apply {
                    isAntiAlias = true
                }
                
                // Draw the darkest image as the base
                canvas.drawBitmap(images[0], 0f, 0f, null)
                
                // Blend the remaining images with different alpha levels
                for (i in 1 until images.size) {
                    paint.alpha = (weights[i] * 255).toInt()
                    canvas.drawBitmap(images[i], 0f, 0f, paint)
                }
                
                return result
            } catch (e: Exception) {
                Log.e(TAG, "Error during exposure fusion: ${e.message}", e)
                return null
            }
        }
        
        /**
         * Calculate blending weights for each image
         */
        private fun calculateWeights(numImages: Int): FloatArray {
            // Simple linear weight distribution
            val weights = FloatArray(numImages)
            
            for (i in 0 until numImages) {
                // For the first image (darkest), use it as the base
                if (i == 0) {
                    weights[i] = 1.0f
                } else {
                    // For subsequent images, use progressively lower weights
                    weights[i] = 0.5f / (numImages - 1)
                }
            }
            
            return weights
        }
    }
} 