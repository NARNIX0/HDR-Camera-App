package com.example.hdrcamera.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.hdrcamera.utils.ImageSaver
import com.example.hdrcamera.utils.ImageUtils

private const val TAG = "CameraScreen"

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Request camera permission
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // Request storage permission (only needed for API <= 28)
    val storagePermissionState = rememberPermissionState(
        permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    
    // Check if we need to request storage permission
    val needsStoragePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    
    // Capture state
    var isCapturing by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HDR Camera") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        if (!cameraPermissionState.status.isGranted) {
            // If camera permission is not granted, show permission request UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is required to use the HDR camera features")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Request Camera Permission")
                }
            }
        } else if (needsStoragePermission && !storagePermissionState.status.isGranted) {
            // If storage permission is needed but not granted, show permission request UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Storage permission is required to save HDR images to your gallery")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { storagePermissionState.launchPermissionRequest() }) {
                    Text("Request Storage Permission")
                }
            }
        } else {
            // All permissions granted, set up the camera preview
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Create PreviewView
                val previewView = remember { PreviewView(context) }
                // Create Preview use case
                val previewUseCase = remember { CameraXPreview.Builder().build() }
                // Create ImageCapture use case
                val imageCaptureUseCase = remember { 
                    ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                }
                
                // Display the PreviewView using AndroidView
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Loading indicator overlay when capturing
                if (isCapturing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .zIndex(10f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Capturing HDR images...",
                                color = Color.White
                            )
                        }
                    }
                }
                
                // Controls overlay
                Box(modifier = Modifier.fillMaxSize()) {
                    // Bottom controls panel
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Shot count state and slider
                        var shotCount by remember { mutableStateOf(3f) }
                        Text(
                            text = "Shots: ${shotCount.toInt()}",
                            color = Color.White
                        )
                        Slider(
                            value = shotCount,
                            onValueChange = { shotCount = it },
                            valueRange = 3f..7f,
                            steps = 1, // 3, 5, 7 shots
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // EV step state and slider
                        var evStep by remember { mutableStateOf(1.0f) }
                        Text(
                            text = "EV Step: +/- ${String.format("%.1f", evStep)}",
                            color = Color.White
                        )
                        Slider(
                            value = evStep,
                            onValueChange = { evStep = it },
                            valueRange = 0.5f..2.0f,
                            steps = 2, // 0.5, 1.0, 1.5, 2.0 steps
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Capture button
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        isCapturing = true
                                        val numShots = shotCount.toInt()
                                        val evValue = evStep
                                        
                                        if (camera != null) {
                                            val cameraInfo = camera!!.cameraInfo
                                            val cameraControl = camera!!.cameraControl
                                            val exposureState = cameraInfo.exposureState
                                            
                                            if (exposureState.isExposureCompensationSupported) {
                                                val exposureRange = exposureState.exposureCompensationRange
                                                val evStep = exposureState.exposureCompensationStep.toFloat()
                                                Log.d(TAG, "EV Range: $exposureRange, EV Step: $evStep")
                                                
                                                // Calculate target exposure indices
                                                val targetEvIndices = calculateEvIndices(
                                                    numShots = numShots,
                                                    evValue = evValue,
                                                    exposureRange = exposureRange,
                                                    evStep = evStep
                                                )
                                                
                                                Log.d(TAG, "Target EV indices: $targetEvIndices")
                                                
                                                // List to store captured image data
                                                val capturedImages = mutableListOf<Bitmap>()
                                                
                                                // Capture images at different exposures
                                                for (evIndex in targetEvIndices) {
                                                    // Set exposure compensation
                                                    cameraControl.setExposureCompensationIndex(evIndex).await()
                                                    
                                                    // Capture image
                                                    val bitmap = captureImage(
                                                        imageCapture = imageCaptureUseCase,
                                                        executor = ContextCompat.getMainExecutor(context)
                                                    )
                                                    
                                                    capturedImages.add(bitmap)
                                                    Log.d(TAG, "Captured image at EV index: $evIndex")
                                                }
                                                
                                                Log.d(TAG, "Captured ${capturedImages.size} images")
                                                
                                                // Save the middle image (best exposed) as HDR 
                                                if (capturedImages.isNotEmpty()) {
                                                    // Check if we need and have storage permission
                                                    val hasStoragePermission = !needsStoragePermission || 
                                                            storagePermissionState.status.isGranted
                                                    
                                                    if (hasStoragePermission) {
                                                        // Get the middle image (usually best exposed)
                                                        val imageToSave = capturedImages[capturedImages.size / 2]
                                                        
                                                        // Save using our new ImageSaver utility
                                                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                                                        val fileName = "HDR_IMG_${timestamp}.jpg"
                                                        val saved = ImageSaver.saveBitmapToGallery(
                                                            context = context,
                                                            bitmap = imageToSave,
                                                            displayName = fileName
                                                        )
                                                        
                                                        if (saved) {
                                                            snackbarHostState.showSnackbar("HDR image saved to gallery")
                                                        } else {
                                                            snackbarHostState.showSnackbar("Failed to save HDR image")
                                                        }
                                                    } else {
                                                        // Show message if we don't have permission
                                                        snackbarHostState.showSnackbar(
                                                            "Storage permission needed to save images"
                                                        )
                                                    }
                                                }
                                            } else {
                                                Log.w(TAG, "Exposure compensation not supported")
                                                snackbarHostState.showSnackbar(
                                                    "Your device doesn't support exposure compensation"
                                                )
                                            }
                                        } else {
                                            Log.e(TAG, "Camera not initialized")
                                            snackbarHostState.showSnackbar("Camera not initialized")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error capturing HDR images", e)
                                        snackbarHostState.showSnackbar(
                                            "Error capturing HDR images: ${e.message}"
                                        )
                                    } finally {
                                        isCapturing = false
                                    }
                                }
                            },
                            enabled = !isCapturing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Capture HDR")
                        }
                    }
                }
                
                // Setup camera provider in a LaunchedEffect
                LaunchedEffect(Unit) {
                    try {
                        // Get the camera provider future
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        
                        // Add a listener to the future to bind camera use cases
                        withContext(Dispatchers.Main) {
                            val cameraProvider = cameraProviderFuture.get()
                            
                            // Unbind any existing use cases
                            cameraProvider.unbindAll()
                            
                            // Select the back camera
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            
                            // Set surface provider for preview
                            previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
                            
                            // Bind the preview use case to lifecycle and store the camera
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                previewUseCase,
                                imageCaptureUseCase
                            )
                        }
                    } catch (e: Exception) {
                        // Handle any errors
                        e.printStackTrace()
                        Log.e(TAG, "Failed to set up camera", e)
                        snackbarHostState.showSnackbar("Failed to start camera: ${e.message}")
                    }
                }
            }
        }
    }
}

// Calculate the EV indices for bracketing
private fun calculateEvIndices(
    numShots: Int,
    evValue: Float,
    exposureRange: android.util.Range<Int>,
    evStep: Float
): List<Int> {
    val result = mutableListOf<Int>()
    
    // For even number of shots, we don't include 0 EV
    // For odd number of shots, we include 0 EV
    val includeZero = numShots % 2 == 1
    val maxSteps = if (includeZero) (numShots - 1) / 2 else numShots / 2
    
    // Calculate the step size in exposure indices
    val stepSizeInEV = evValue
    val stepSizeInIndices = (stepSizeInEV / evStep).roundToInt()
    
    if (includeZero) {
        // Add the zero (base) exposure
        result.add(0)
        
        // Add the bracketed exposures
        for (i in 1..maxSteps) {
            val positiveEV = i * stepSizeInIndices
            val negativeEV = -i * stepSizeInIndices
            
            if (positiveEV <= exposureRange.upper) {
                result.add(positiveEV)
            }
            
            if (negativeEV >= exposureRange.lower) {
                result.add(negativeEV)
            }
        }
    } else {
        // Even number of shots, don't include 0 EV
        for (i in 1..maxSteps) {
            val halfStep = stepSizeInIndices / 2
            val positiveEV = (2 * i - 1) * halfStep
            val negativeEV = -(2 * i - 1) * halfStep
            
            if (positiveEV <= exposureRange.upper) {
                result.add(positiveEV)
            }
            
            if (negativeEV >= exposureRange.lower) {
                result.add(negativeEV)
            }
        }
    }
    
    // Sort the indices to ensure they're in order
    return result.sorted()
}

// Function to capture an image and convert to Bitmap
private suspend fun captureImage(
    imageCapture: ImageCapture,
    executor: Executor
): Bitmap = suspendCancellableCoroutine { continuation ->
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = image.toBitmap()
                    continuation.resume(bitmap)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                } finally {
                    image.close()
                }
            }
            
            override fun onError(exception: ImageCaptureException) {
                continuation.resumeWithException(exception)
            }
        }
    )
}

// Extension function to convert ImageProxy to Bitmap
private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size).let { bitmap ->
        // Handle rotation if needed
        val matrix = Matrix().apply {
            postRotate(imageInfo.rotationDegrees.toFloat())
        }
        
        Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Preview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    CameraScreen()
} 