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
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isSessionAuthorized = MutableStateFlow(false)
    private val _onNewPermissionGranted = MutableSharedFlow<Unit>()
    override val onNewPermissionGranted: Flow<Unit> = _onNewPermissionGranted.asSharedFlow()

    private val pollTrigger = MutableStateFlow(0L)

    init {
        monitorScope.launch {
            while (true) {
                pollTrigger.value = System.currentTimeMillis()
                delay(3000)
            }
        }
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
                        _isSessionAuthorized.value = false
                        trySend(Unit)
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        _isSessionAuthorized.value = false
                        trySend(Unit)
                    }
                    ACTION_USB_PERMISSION -> {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (granted && device != null) {
                            prefs.edit(commit = true) { putString(KEY_LAST_AUTHORIZED_VID_PID, getHardwareId(device)) }
                            _isSessionAuthorized.value = true
                            
                            if (prefs.getBoolean(KEY_IS_FIRST_SETUP_COMPLETE, false)) {
                                monitorScope.launch { _onNewPermissionGranted.emit(Unit) }
                            } else {
                                prefs.edit(commit = true) { putBoolean(KEY_IS_FIRST_SETUP_COMPLETE, true) }
                            }
                        }
                        isDialogShowing = false
                        trySend(Unit)
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
        pollTrigger,
        _isSessionAuthorized
    ) { _, _, sessionAuth ->
        val deviceList = usbManager.deviceList
        var found = false
        val lastAuthorized = prefs.getString(KEY_LAST_AUTHORIZED_VID_PID, "")
        val isBoot = (System.currentTimeMillis() - appStartTime) < 20000

        for (device in deviceList.values) {
            if (isVideoDevice(device)) {
                val hardwareId = getHardwareId(device)
                val hasPermission = usbManager.hasPermission(device)
                
                if (hasPermission || sessionAuth || (isBoot && hardwareId == lastAuthorized)) {
                    if (isBoot && hardwareId == lastAuthorized && !_isSessionAuthorized.value) {
                        _isSessionAuthorized.value = true
                    }
                    found = true
                    break
                } else if (!isBoot && !isDialogShowing && System.currentTimeMillis() > requestSuppressionEndTime) {
                    requestUsbPermission(device)
                }
            }
        }
        found
    }.distinctUntilChanged().stateIn(monitorScope, SharingStarted.Eagerly, false)

    override fun triggerCheck() {
        pollTrigger.value = System.currentTimeMillis()
    }

    override fun suppressAutomatedRequests(durationMillis: Long) {
        requestSuppressionEndTime = System.currentTimeMillis() + durationMillis
    }

    private fun isVideoDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_VIDEO) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO) return true
        }
        return false
    }

    private fun getHardwareId(device: UsbDevice): String = "${device.vendorId}:${device.productId}"

    private fun requestUsbPermission(device: UsbDevice) {
        if (isDialogShowing) return
        try {
            val intent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
                putExtra(UsbManager.EXTRA_DEVICE, device)
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
            isDialogShowing = true
            usbManager.requestPermission(device, permissionIntent)
        } catch (e: Exception) {
            isDialogShowing = false
        }
    }
}
