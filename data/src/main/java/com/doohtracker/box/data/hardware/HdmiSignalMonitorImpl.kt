package com.doohtracker.box.data.hardware

import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class HdmiSignalMonitorImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : HdmiSignalMonitor {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /**
     * Detects HDMI Signal presence by:
     * 1. Querying connected USB devices (using the provided snippet logic).
     * 2. Verifying if any device belongs to the Video Class (UVC).
     * 3. Cross-referencing with CameraManager availability.
     */
    override val isSignalActive: Flow<Boolean> = flow {
        while (true) {
            try {
                val cameraIds = cameraManager.cameraIdList
                
                // Query connected USB devices using the requested logic
                val usbDevices = usbManager.deviceList.values
                var isUsbVideoDeviceFound = false
                
                for (device in usbDevices) {
                    Log.d("USB", device.deviceName)
                    
                    // HDMI Capture cards are standard UVC (USB Video Class)
                    if (isVideoDevice(device) && usbManager.hasPermission(device)) {
                        isUsbVideoDeviceFound = true
                        break
                    }
                }

                // The HDMI Signal is considered active if a Video device is found via USB (with permission)
                // AND it is registered in the Android Camera system.
                emit(isUsbVideoDeviceFound && cameraIds.isNotEmpty())
            } catch (e: Exception) {
                Log.e("HDMI", "Error monitoring signal", e)
                emit(false)
            }
            
            delay(5000.milliseconds)
        }
    }

    private fun isVideoDevice(device: android.hardware.usb.UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_VIDEO) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO) return true
        }
        return false
    }
}
