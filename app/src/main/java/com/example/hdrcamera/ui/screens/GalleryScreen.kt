package com.example.hdrcamera.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.hdrcamera.utils.ImageUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "GalleryScreen"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun GalleryScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Determine which permission to request based on Android version
    val permissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    // Add lifecycle observer to refresh when screen becomes visible
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && permissionState.status.isGranted) {
                loadImages(context) { loadedImages ->
                    images = loadedImages
                    isLoading = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Load images when permission is granted
    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            loadImages(context) { loadedImages ->
                images = loadedImages
                isLoading = false
                
                // Debug - log number of images found
                Log.d(TAG, "Found ${loadedImages.size} images")
                if (loadedImages.isEmpty()) {
                    Log.d(TAG, "No images found. Make sure you've captured at least one image.")
                } else {
                    Log.d(TAG, "First image URI: ${loadedImages.first()}")
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HDR Gallery") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!permissionState.status.isGranted) {
            // Show permission request UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Storage permission is required to access your HDR images")
                androidx.compose.material3.Button(
                    onClick = { 
                        permissionState.launchPermissionRequest()
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Grant Permission")
                }
            }
        } else {
            // Permission granted, display images
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (images.isEmpty()) {
                    // No images found
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("No HDR images captured yet")
                        Text(
                            "Capture some HDR photos in the camera screen",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    // Display images in a grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(images) { uri ->
                            ImageItem(uri = uri)
                        }
                    }
                }
            }
        }
    }
}

private fun loadImages(context: android.content.Context, callback: (List<Uri>) -> Unit) {
    MainScope().launch(Dispatchers.IO) {
        try {
            // First try MediaStore approach
            var loadedImages = ImageUtils.getHDRImages(context)
            Log.d(TAG, "MediaStore query found: ${loadedImages.size} images")
            
            // If MediaStore found nothing, try direct filesystem access
            if (loadedImages.isEmpty()) {
                loadedImages = ImageUtils.getHDRImagesFromFileSystem(context)
                Log.d(TAG, "Filesystem method found: ${loadedImages.size} images")
            }
            
            // If still no images, try the fallback method with the most recent file
            if (loadedImages.isEmpty()) {
                val mostRecentFile = ImageUtils.getMostRecentHDRImageFile(context)
                
                if (mostRecentFile != null && mostRecentFile.exists()) {
                    // Use FileProvider to get a content:// URI
                    try {
                        val fileProviderUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            mostRecentFile
                        )
                        Log.d(TAG, "Using most recent file via FileProvider: $fileProviderUri")
                        loadedImages = listOf(fileProviderUri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting FileProvider URI for most recent file", e)
                        // Fallback to file:// URI if FileProvider fails
                        val fileUri = Uri.fromFile(mostRecentFile)
                        Log.d(TAG, "Using file:// URI as fallback: $fileUri")
                        loadedImages = listOf(fileUri)
                    }
                }
            }
            
            // Filter out any inaccessible images
            val accessibleImages = loadedImages.filter { uri ->
                try {
                    // For content URIs, check if we can get the MIME type
                    if (uri.scheme == "content") {
                        val type = context.contentResolver.getType(uri)
                        val hasType = type != null
                        if (!hasType) {
                            Log.e(TAG, "Cannot get MIME type for URI: $uri")
                        }
                        hasType
                    } 
                    // For file URIs, check if the file exists
                    else if (uri.scheme == "file") {
                        val file = File(uri.path ?: "")
                        val exists = file.exists()
                        if (!exists) {
                            Log.e(TAG, "File does not exist: ${file.absolutePath}")
                        }
                        exists
                    } 
                    // For other schemes, assume they're accessible
                    else {
                        true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking URI accessibility: $uri", e)
                    false
                }
            }
            
            Log.d(TAG, "Filtered down to ${accessibleImages.size} accessible images")
            
            withContext(Dispatchers.Main) {
                callback(accessibleImages)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading images", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error loading images: ${e.message}", Toast.LENGTH_SHORT).show()
                callback(emptyList())
            }
        }
    }
}

@Composable
fun ImageItem(uri: Uri) {
    val context = LocalContext.current
    Log.d(TAG, "Loading image with URI: $uri (scheme: ${uri.scheme})")
    
    // States for image loading
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    
    // Convert file:// URIs to content:// URIs using FileProvider if needed
    val finalUri = remember(uri) {
        if (uri.scheme == "file") {
            try {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    Log.d(TAG, "Converting file:// URI to FileProvider URI: ${file.absolutePath}")
                    val providerUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    Log.d(TAG, "Converted to FileProvider URI: $providerUri")
                    providerUri
                } else {
                    Log.e(TAG, "File does not exist: ${file.absolutePath}")
                    uri
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error converting file URI to FileProvider URI", e)
                uri
            }
        } else {
            uri
        }
    }
    
    // Load the bitmap directly
    LaunchedEffect(finalUri) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading bitmap from URI: $finalUri")
                val inputStream = context.contentResolver.openInputStream(finalUri)
                if (inputStream != null) {
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream.close()
                    
                    // Report image dimensions from metadata
                    Log.d(TAG, "Image dimensions: ${options.outWidth}x${options.outHeight}")
                    
                    // Reopen the stream to decode the actual bitmap
                    val inputStream2 = context.contentResolver.openInputStream(finalUri)
                    if (inputStream2 != null) {
                        val decodingOptions = android.graphics.BitmapFactory.Options().apply {
                            // Use a smaller sample size for better quality
                            inSampleSize = 1 
                        }
                        val loadedBitmap = android.graphics.BitmapFactory.decodeStream(inputStream2, null, decodingOptions)
                        inputStream2.close()
                        
                        if (loadedBitmap != null) {
                            Log.d(TAG, "Successfully loaded bitmap: ${loadedBitmap.width}x${loadedBitmap.height}")
                            withContext(Dispatchers.Main) {
                                bitmap = loadedBitmap
                                isLoading = false
                            }
                        } else {
                            Log.e(TAG, "Failed to decode bitmap")
                            withContext(Dispatchers.Main) {
                                loadError = "Failed to decode image"
                                isLoading = false
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Cannot open input stream for URI: $finalUri")
                    withContext(Dispatchers.Main) {
                        loadError = "Cannot open image"
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading bitmap", e)
                withContext(Dispatchers.Main) {
                    loadError = e.message
                    isLoading = false
                }
            }
        }
    }
    
    // Main UI
    Box(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> {
                // Display the bitmap
                AndroidView(
                    factory = { ctx ->
                        android.widget.ImageView(ctx).apply {
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            setImageBitmap(bitmap)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Show image dimensions in debug overlay
                Text(
                    "${bitmap?.width}x${bitmap?.height}",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp)
                )
            }
            loadError != null -> {
                // Error state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        "Error: $loadError",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.Red
                    )
                    // Add retry button
                    androidx.compose.material3.Button(
                        onClick = { 
                            isLoading = true
                            loadError = null
                            // Trigger LaunchedEffect again by recreating the key
                            uri.toString() + System.currentTimeMillis()
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

// Calculate sample size for efficiently loading large bitmaps
private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    
    Log.d(TAG, "Calculated sample size: $inSampleSize for image ${width}x${height}")
    return inSampleSize
}

@Preview(showBackground = true)
@Composable
fun GalleryScreenPreview() {
    GalleryScreen()
} 