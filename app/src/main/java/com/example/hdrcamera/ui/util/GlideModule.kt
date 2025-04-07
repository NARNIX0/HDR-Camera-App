package com.example.hdrcamera.ui.util

import android.content.Context
import android.util.Log
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

private const val TAG = "GlideModule"

/**
 * Custom Glide module for the HDR Camera app.
 * This configures Glide with appropriate settings for high-quality image loading.
 */
@GlideModule
class HDRCameraGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setLogLevel(Log.VERBOSE) // Enable verbose logging for debugging
            .setDefaultRequestOptions(
                RequestOptions()
                    .format(DecodeFormat.PREFER_ARGB_8888) // Use higher quality image format
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both original and resized images
            )
            
        Log.d(TAG, "HDRCameraGlideModule initialized with verbose logging")
    }
    
    // Disabling manifest parsing improves performance
    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
} 