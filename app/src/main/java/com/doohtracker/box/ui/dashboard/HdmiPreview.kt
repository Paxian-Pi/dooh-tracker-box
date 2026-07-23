package com.doohtracker.box.ui.dashboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("MissingPermission")
@Composable
fun HdmiPreview(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    
    var cameraId by remember { mutableStateOf<String?>(null) }
    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var captureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }
    var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Check permissions
    val hasPermission = remember {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    if (!hasPermission) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Camera Permission Required", color = Color.White)
        }
        return
    }

    // Availability Callback for faster discovery
    DisposableEffect(cameraManager) {
        val callback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(id: String) {
                Log.d("HdmiPreview", "Camera $id became available via callback")
                if (cameraId == null) {
                    cameraId = id
                    error = null
                }
            }
        }
        cameraManager.registerAvailabilityCallback(callback, mainHandler)
        onDispose { cameraManager.unregisterAvailabilityCallback(callback) }
    }

    // Polling for Camera availability - Hardware often has latency after USB permission
    LaunchedEffect(cameraId) {
        if (cameraId != null) return@LaunchedEffect
        
        var attempts = 0
        while (cameraId == null && attempts < 30) {
            val ids = try { cameraManager.cameraIdList } catch (e: Exception) { emptyArray<String>() }
            Log.d("HdmiPreview", "Probing cameras... Found IDs: ${ids.joinToString()}")
            
            if (ids.isNotEmpty()) {
                // RK Boxes often map UVC to the first ID. We'll take any camera as a last resort.
                val id = findHdmiCameraId(cameraManager)
                if (id != null) {
                    Log.d("HdmiPreview", "Found suitable Camera ID: $id")
                    cameraId = id
                    error = null
                    break
                } else {
                    Log.d("HdmiPreview", "Camera(s) found but didn't match HDMI criteria. Forcing first available.")
                    cameraId = ids.first()
                    error = null
                    break
                }
            }
            
            error = "Initializing HDMI Hardware (${attempts + 1}/30)..."
            attempts++
            delay(1000.milliseconds)
        }
        if (cameraId == null) {
            error = "No HDMI Media Source Detected\nPlease verify the USB connection."
        }
    }

    LaunchedEffect(cameraDevice, surfaceTexture) {
        val device = cameraDevice
        val st = surfaceTexture
        if (device != null && st != null) {
            Log.d("HdmiPreview", "Starting preview session...")
            startPreview(device, st) { session ->
                captureSession = session
            }
        }
    }

    DisposableEffect(cameraId) {
        val id = cameraId
        if (id != null) {
            Log.d("HdmiPreview", "Attempting to open camera: $id")
            try {
                cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        Log.d("HdmiPreview", "onOpened: Camera $id is now open and rendering")
                        cameraDevice = camera
                        error = null
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Log.w("HdmiPreview", "onDisconnected: Camera $id disconnected")
                        cameraDevice = null
                        camera.close()
                    }

                    override fun onError(camera: CameraDevice, err: Int) {
                        Log.e("HdmiPreview", "onError: Camera $id error code $err")
                        cameraDevice = null
                        camera.close()
                        error = "Hardware Error: $err"
                    }
                }, mainHandler)
            } catch (e: Exception) {
                error = "Access Denied"
                Log.e("HdmiPreview", "Exception opening camera", e)
            }
        }

        onDispose {
            Log.d("HdmiPreview", "Cleaning up HDMI stream...")
            captureSession?.close()
            cameraDevice?.close()
            captureSession = null
            cameraDevice = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            surfaceTexture = st
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            surfaceTexture = null
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (error != null || cameraDevice == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error ?: "Initializing HDMI Stream...",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold)
                    )
                    
                    if (error?.contains("Detected") == true) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { 
                            cameraId = null
                            error = null
                        }) {
                            Text("Retry Discovery")
                        }
                    }
                }
            }
        }
    }
}

private fun findHdmiCameraId(cameraManager: CameraManager): String? {
    return try {
        val ids = cameraManager.cameraIdList
        if (ids.isEmpty()) return null
        
        Log.d("HdmiPreview", "Analyzing ${ids.size} available camera(s)")
        
        val externalIds = mutableListOf<String>()
        val otherIds = mutableListOf<String>()

        for (id in ids) {
            val chars = cameraManager.getCameraCharacteristics(id)
            
            // Log full details for debugging RK Boxes
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            Log.d("HdmiPreview", "Camera $id: facing=$facing, caps=${capabilities?.joinToString()}")

            if (facing == CameraMetadata.LENS_FACING_EXTERNAL) {
                externalIds.add(id)
            } else {
                otherIds.add(id)
            }
        }

        // Priority 1: explicitly EXTERNAL
        if (externalIds.isNotEmpty()) return externalIds.first()

        // Priority 2: Any camera supporting preview (likely the only device)
        if (otherIds.isNotEmpty()) return otherIds.first()

        null
    } catch (e: Exception) {
        Log.e("HdmiPreview", "Error during camera ID analysis", e)
        null
    }
}

private fun startPreview(
    camera: CameraDevice?, 
    surfaceTexture: SurfaceTexture,
    onSessionCreated: (CameraCaptureSession) -> Unit
) {
    if (camera == null) return
    
    // Set default buffer size for high compatibility (720p is standard for UVC cards)
    surfaceTexture.setDefaultBufferSize(1280, 720)
    
    val surface = Surface(surfaceTexture)
    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    builder.addTarget(surface)

    try {
        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    session.setRepeatingRequest(builder.build(), null, null)
                    onSessionCreated(session)
                } catch (e: Exception) {
                    Log.e("HdmiPreview", "Failed to start repeating request", e)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e("HdmiPreview", "Configuration failed")
            }
        }, null)
    } catch (e: Exception) {
        Log.e("HdmiPreview", "Failed to create capture session", e)
    }
}
