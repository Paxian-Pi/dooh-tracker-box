package com.doohtracker.box.ui.dashboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
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
import androidx.tv.material3.Text
import com.doohtracker.box.data.hardware.SignalMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.graphics.get

@SuppressLint("MissingPermission")
@Composable
fun HdmiPreview(
    modifier: Modifier = Modifier,
    signalMonitor: SignalMonitor? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var cameraId by remember { mutableStateOf<String?>(null) }
    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var captureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }
    var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var textureViewInstance by remember { mutableStateOf<TextureView?>(null) }

    // HDMI Signal Analyzer: Distinguishes Laptop data from "No Signal" noise/bars
    LaunchedEffect(textureViewInstance, cameraDevice) {
        val tv = textureViewInstance ?: return@LaunchedEffect
        var lastPixelsSum = 0L

        while (true) {
            delay(1000.milliseconds)
            if (cameraDevice == null) continue

            try {
                // Use a slightly larger grid (32x32) for better spatial complexity analysis
                val bitmap = tv.getBitmap(32, 32)
                if (bitmap != null) {
                    var currentPixelsSum = 0L
                    var maxBrightness = 0
                    var totalSaturation = 0L
                    var spatialComplexity = 0L

                    val w = bitmap.width
                    val h = bitmap.height

                    for (x in 0 until w) {
                        for (y in 0 until h) {
                            val pixel = bitmap[x, y]
                            val r = (pixel shr 16) and 0xFF
                            val g = (pixel shr 8) and 0xFF
                            val b = pixel and 0xFF

                            // 1. Brightness & Sum
                            val lum = (r + g + b) / 3
                            currentPixelsSum += (r + g + b)
                            if (lum > maxBrightness) maxBrightness = lum

                            // 2. Saturation (Colorfulness)
                            val max = maxOf(r, maxOf(g, b))
                            val min = minOf(r, minOf(g, b))
                            totalSaturation += (max - min)

                            // 3. Spatial Complexity (Neighbor difference)
                            if (x < w - 1) {
                                val nextPixel = bitmap[x + 1, y]
                                val nr = (nextPixel shr 16) and 0xFF
                                spatialComplexity += kotlin.math.abs(r - nr)
                            }
                        }
                    }

                    val diff = kotlin.math.abs(currentPixelsSum - lastPixelsSum)
                    val avgSaturation = totalSaturation / (w * h)
                    val normalizedComplexity = spatialComplexity / (w * h)

                    /**
                     * Signal Validation Logic:
                     * - isMoving: Detects changes (video, mouse, clock).
                     * - isComplex: Detects UI elements (text, icons, wallpaper). 
                     *   "No Signal" screens are usually solid colors or simple bars (low complexity).
                     * - isNotNoise: Jittery noise cards have high 'diff' but very low saturation (grey).
                     */
                    val isMoving = diff > 4000
                    val isComplex = normalizedComplexity > 12
                    val isNotBlack = maxBrightness > 35
                    val isNotNoise = !(diff > 10000 && avgSaturation < 20)

                    val isRealSignal = isNotBlack && isNotNoise && (isMoving || isComplex)

                    if (isRealSignal) {
                        signalMonitor?.reportHeartbeat()
                    }

                    lastPixelsSum = currentPixelsSum
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Log.e("HdmiPreview", "Link Analysis failed", e)
            }
        }
    }

    val isUsbConnected by (signalMonitor?.isUsbConnected ?: flowOf(true)).collectAsState(initial = true)

    // Probing Loop: Strictly locked to HDMI hardware
    LaunchedEffect(Unit) {
        var attempts = 0
        val isTvBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                context.packageManager.hasSystemFeature("android.hardware.type.television")

        while (cameraId == null && attempts < 60) {
            val hasPermission =
                context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                error = "Camera Permission Required.\nPlease allow access when prompted."
                delay(1000.milliseconds)
                continue
            }

            val ids = try {
                cameraManager.cameraIdList
            } catch (e: Exception) {
                Log.e("HdmiPreview", "Failed to get camera list", e)
                emptyArray<String>()
            }

            Log.d("HdmiPreview", "Found cameras: ${ids.joinToString()}")

            // 1. Preferred: Find device labeled as EXTERNAL
            var targetId = ids.find { id ->
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_EXTERNAL
                } catch (e: Exception) {
                    false
                }
            }

            // 2. Robust Fallback: On TV boxes or specialized hardware, the capture card 
            // might be the only camera, or mislabeled.
            if (targetId == null) {
                targetId = ids.find { id ->
                    try {
                        val chars = cameraManager.getCameraCharacteristics(id)
                        val facing = chars.get(CameraCharacteristics.LENS_FACING)
                        facing != CameraMetadata.LENS_FACING_FRONT && facing != CameraMetadata.LENS_FACING_BACK
                    } catch (e: Exception) {
                        false
                    }
                }
            }

            // 3. Last Resort: If we are on a TV box, or we have more than 2 cameras (indicating an external one),
            // or if it's the only camera available, use the last one.
            // External USB cameras are almost always appended to the end of the list.
            if (targetId == null && ids.isNotEmpty()) {
                if (isTvBox || ids.size == 1 || (isUsbConnected && ids.size > 2)) {
                    targetId = ids.last()
                }
            }

            if (targetId != null) {
                Log.i("HdmiPreview", "Selected Camera ID: $targetId")
                cameraId = targetId
                error = null
                break
            }

            error = if (isUsbConnected) {
                "USB Card detected, waiting for Camera system... (${attempts}/60)"
            } else {
                "Searching for HDMI Source... (${attempts}/60)\nEnsure USB Permissions are granted."
            }
            attempts++
            delay(1000.milliseconds)
        }

        if (cameraId == null) {
            val hasPermission =
                context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            error = if (!hasPermission) {
                "Camera Permission Denied.\nPlease enable it in App Settings."
            } else if (isUsbConnected) {
                "HDMI Capture Card detected but Camera Access failed.\nTry reconnecting the USB cable."
            } else {
                "No HDMI Media Source Found.\nEnsure 'OTG' is enabled in Settings."
            }
        }
    }

    LaunchedEffect(cameraDevice, surfaceTexture) {
        val device = cameraDevice
        val st = surfaceTexture
        if (device != null && st != null) {
            startPreview(device, st) { session ->
                captureSession = session
            }
        }
    }

    DisposableEffect(cameraId) {
        val id = cameraId
        if (id != null) {
            try {
                cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        error = null
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        cameraDevice = null
                        camera.close()
                    }

                    override fun onError(camera: CameraDevice, err: Int) {
                        cameraDevice = null
                        camera.close()
                        error = "Hardware Error: $err"
                    }
                }, mainHandler)
            } catch (e: Exception) {
                error = "Access Denied"
            }
        }
        onDispose {
            captureSession?.close()
            cameraDevice?.close()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    textureViewInstance = this
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error ?: "Connecting HDMI Stream...",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

private fun startPreview(
    camera: CameraDevice,
    surfaceTexture: SurfaceTexture,
    onSessionCreated: (CameraCaptureSession) -> Unit
) {
    surfaceTexture.setDefaultBufferSize(1280, 720)
    val previewSurface = Surface(surfaceTexture)
    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    builder.addTarget(previewSurface)

    try {
        camera.createCaptureSession(listOf(previewSurface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    session.setRepeatingRequest(builder.build(), null, null)
                    onSessionCreated(session)
                } catch (e: Exception) {
                    Log.e("HdmiPreview", "Session Config failed", e)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e("HdmiPreview", "Configuration failed")
            }
        }, null)
    } catch (e: Exception) {
        Log.e("HdmiPreview", "Fatal error creating session", e)
    }
}
