package com.doohtracker.box.data.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Singleton
class UsbConnectionMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UsbConnectionMonitor {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val ACTION_USB_PERMISSION = "com.doohtracker.box.USB_PERMISSION"
    
    private val prefs = context.getSharedPreferences("usb_prefs", Context.MODE_PRIVATE)
    private val KEY_LAST_AUTHORIZED_VID_PID = "last_authorized_vid_pid"
    private val KEY_IS_FIRST_SETUP_COMPLETE = "is_first_setup_complete"

    private val pendingPermissionRequests = mutableSetOf<String>()
    private var isDialogShowing = false
    private var lastResultReceivedTime = 0L
    private var requestSuppressionEndTime = 0L
    
    private val appStartTime = System.currentTimeMillis()
    private val checkMutex = Mutex()
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Tracks if the current device in this session is considered "Authorized"
    private val _isSessionAuthorized = MutableStateFlow(false)

    private val _onNewPermissionGranted = MutableSharedFlow<Unit>()
    override val onNewPermissionGranted: Flow<Unit> = _onNewPermissionGranted.asSharedFlow()

    private val checkTriggers = MutableSharedFlow<Unit>(replay = 1)

    init {
        checkTriggers.tryEmit(Unit)
    }

    private val usbEvents = callbackFlow<Unit> {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action ?: return
                Log.d("USB", "Broadcast received: $action")
                
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                when (action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        device?.let { dev ->
                            if (isVideoDevice(dev)) {
                                val hardwareId = getHardwareId(dev)
                                val lastAuthorized = prefs.getString(KEY_LAST_AUTHORIZED_VID_PID, "")
                                val isBoot = (System.currentTimeMillis() - appStartTime) < 20000

                                if (isBoot && hardwareId == lastAuthorized) {
                                    Log.d("USB", "Boot: Recognized hardware found. Auto-authorizing session.")
                                    _isSessionAuthorized.value = true
                                } else if (!isBoot) {
                                    Log.d("USB", "Manual Attachment: Requester permission for $hardwareId")
                                    _isSessionAuthorized.value = false
                                    requestUsbPermission(dev)
                                }
                            }
                        }
                        trySend(Unit)
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        Log.d("USB", "Device Detached. Resetting session.")
                        _isSessionAuthorized.value = false
                        trySend(Unit)
                    }
                    ACTION_USB_PERMISSION -> {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        Log.d("USB", "Permission Result: $granted")
                        
                        lastResultReceivedTime = System.currentTimeMillis()
                        
                        device?.let { dev ->
                            val hardwareId = getHardwareId(dev)

                            if (granted) {
                                prefs.edit(commit = true) { putString(KEY_LAST_AUTHORIZED_VID_PID, hardwareId) }
                                _isSessionAuthorized.value = true
                                
                                val isFirstSetupComplete = prefs.getBoolean(KEY_IS_FIRST_SETUP_COMPLETE, false)
                                // Trigger Restart Dialog if not the boot phase
                                if (isFirstSetupComplete && (System.currentTimeMillis() - appStartTime > 30000)) {
                                    monitorScope.launch { _onNewPermissionGranted.emit(Unit) }
                                } else if (!isFirstSetupComplete) {
                                    prefs.edit(commit = true) { putBoolean(KEY_IS_FIRST_SETUP_COMPLETE, true) }
                                }
                            } else {
                                _isSessionAuthorized.value = false
                            }
                            
                            monitorScope.launch {
                                delay(5.seconds) 
                                isDialogShowing = false
                                pendingPermissionRequests.remove(hardwareId)
                                trySend(Unit)
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    override val isUsbDeviceConnected: Flow<Boolean> = combine(
        usbEvents.onStart { emit(Unit) },
        checkTriggers,
        _isSessionAuthorized,
        flow { 
            while (true) {
                emit(Unit)
                delay(5000.milliseconds) 
            }
        }
    ) { _, _, sessionAuth, _ ->
        checkAndSyncHardwareState(sessionAuth)
    }
    .distinctUntilChanged()
    .shareIn(
        scope = monitorScope,
        started = SharingStarted.Eagerly,
        replay = 1
    )

    override fun triggerCheck() {
        checkTriggers.tryEmit(Unit)
    }

    override fun suppressAutomatedRequests(durationMillis: Long) {
        requestSuppressionEndTime = System.currentTimeMillis() + durationMillis
    }

    private suspend fun checkAndSyncHardwareState(sessionAuth: Boolean): Boolean = checkMutex.withLock {
        val deviceList = usbManager.deviceList
        var foundActive = false
        val currentTime = System.currentTimeMillis()
        val isBootWindow = (currentTime - appStartTime) < 15000
        val lastAuthorized = prefs.getString(KEY_LAST_AUTHORIZED_VID_PID, "")

        for (device in deviceList.values) {
            if (isVideoDevice(device)) {
                val hasOsPermission = usbManager.hasPermission(device)
                val hardwareId = getHardwareId(device)

                // LOGIC:
                // If it's the boot window and matches historical hardware -> Auto-Active
                // If the user already clicked OK in this session -> Active
                // If OS somehow remembers the permission -> Active
                if (hasOsPermission || sessionAuth || (isBootWindow && hardwareId == lastAuthorized)) {
                    if (isBootWindow && hardwareId == lastAuthorized && !sessionAuth) {
                        Log.d("USB", "Boot Polling: Known hardware $hardwareId found. Restoring active state.")
                        _isSessionAuthorized.value = true
                    }
                    foundActive = true
                } else if (!isBootWindow) {
                    // Not in boot window and not authorized -> Manual intervention required
                    Log.d("USB", "Polling: Unauthorized device $hardwareId found. Requesting permission.")
                    requestUsbPermission(device)
                }
            }
        }
        return foundActive
    }

    private fun isVideoDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_VIDEO) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO) return true
        }
        return false
    }

    private fun getHardwareId(device: UsbDevice): String {
        return "${device.vendorId}:${device.productId}"
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val hardwareId = getHardwareId(device)
        val currentTime = System.currentTimeMillis()
        
        if (isDialogShowing || pendingPermissionRequests.contains(hardwareId)) return
        if (currentTime - lastResultReceivedTime < 10000) return
        if (currentTime < requestSuppressionEndTime) return

        try {
            Log.d("USB", "ACTION: requestPermission for $hardwareId")
            val intent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
                putExtra(UsbManager.EXTRA_DEVICE, device)
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
            
            pendingPermissionRequests.add(hardwareId)
            isDialogShowing = true
            usbManager.requestPermission(device, permissionIntent)
        } catch (e: Exception) {
            Log.e("USB", "Error requesting USB permission", e)
            isDialogShowing = false
            pendingPermissionRequests.remove(hardwareId)
        }
    }
}
