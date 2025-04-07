package com.example.hdrcamera

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.compose.LocalImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.hdrcamera.ui.screens.CameraScreen
import com.example.hdrcamera.ui.screens.GalleryScreen
import com.example.hdrcamera.ui.screens.HomeScreen
import com.example.hdrcamera.ui.theme.HDRCameraTheme
import java.io.File

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    
    // Create a custom image loader for better handling of file URIs
    @OptIn(ExperimentalCoilApi::class)
    private val imageLoader by lazy {
        ImageLoader.Builder(this)
            .crossfade(true)
            .respectCacheHeaders(false) // Important for local files
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(this.cacheDir, "image_cache"))
                    .maxSizePercent(0.1)
                    .build()
            }
            .build()
    }
    
    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Log all permission results
        permissions.entries.forEach { entry ->
            Log.d(TAG, "Permission ${entry.key}: ${if (entry.value) "GRANTED" else "DENIED"}")
        }
        
        // Check if we have camera permission
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(this, "Camera permission is required for this app", Toast.LENGTH_LONG).show()
        }
        
        // Check if we have storage permissions
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }
        
        if (!storagePermission) {
            Toast.makeText(this, "Storage permission is required to save and view images", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request required permissions
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
        )
        
        // Add storage permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Adding READ_MEDIA_IMAGES permission for Android 13+")
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            Log.d(TAG, "Adding READ_EXTERNAL_STORAGE permission for Android < 13")
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                Log.d(TAG, "Adding WRITE_EXTERNAL_STORAGE permission for Android 9 or below")
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        Log.d(TAG, "Requesting permissions: ${permissions.joinToString()}")
        requestPermissions.launch(permissions.toTypedArray())
        
        setContent {
            // Provide our custom image loader
            androidx.compose.runtime.CompositionLocalProvider(
                LocalImageLoader provides imageLoader
            ) {
                HDRCameraTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToCamera = { navController.navigate("camera") },
                onNavigateToGallery = { navController.navigate("gallery") }
            )
        }
        
        composable("camera") {
            CameraScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("gallery") {
            GalleryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HDRCameraTheme {
        Greeting("Android")
    }
}