package com.doohtracker.box

import android.content.pm.ActivityInfo
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.doohtracker.box.data.hardware.UsbConnectionMonitor
import com.doohtracker.box.data.repository.CampaignRepository
import com.doohtracker.box.data.sync.SyncManager
import com.doohtracker.box.tracker.service.TrackerService
import com.doohtracker.box.ui.activation.ActivationScreen
import com.doohtracker.box.ui.activation.ActivationViewModel
import com.doohtracker.box.ui.dashboard.DashboardScreen
import com.doohtracker.box.ui.dashboard.DashboardViewModel
import com.doohtracker.box.ui.theme.DoohTrackerBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var usbConnectionMonitor: UsbConnectionMonitor

    @Inject
    lateinit var campaignRepository: CampaignRepository

    private var isActivated by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        usbConnectionMonitor.triggerCheck()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Block automated requests if OS handled the launch via USB
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            usbConnectionMonitor.suppressAutomatedRequests(30000L)
        }

        isActivated = campaignRepository.isDeviceActivated()

        // Request permissions on launch
        requestPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        
        syncManager.startImmediateSync()
        syncManager.scheduleCampaignSync()

        startForegroundService(Intent(this, TrackerService::class.java))

        lifecycleScope.launch {
            delay(2.seconds)
            usbConnectionMonitor.triggerCheck()
        }

        setContent {
            val isDarkMode by campaignRepository.isDarkModeEnabled.collectAsState(initial = true)
            DoohTrackerBoxTheme(isInDarkTheme = isDarkMode) {
                // Handle orientation based on activation state
                LaunchedEffect(isActivated) {
                    if (isActivated) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else if (campaignRepository.getActivationCodes().isEmpty()) {
                        // Only force portrait if we have NO sites at all
//                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    if (isActivated) {
                        val viewModel: DashboardViewModel = hiltViewModel()
                        LaunchedEffect(viewModel) {
                            viewModel.onLogoutSuccess.collect {
                                // Restart activity with a fresh intent to clear all ViewModel stores
                                val freshIntent = Intent(this@MainActivity, MainActivity::class.java)
                                finish()
                                startActivity(freshIntent)
                            }
                        }
                        LaunchedEffect(viewModel) {
                            viewModel.onAddIdentity.collect {
                                isActivated = false
                            }
                        }
                        DashboardScreen(viewModel)
                    } else {
                        val viewModel: ActivationViewModel = hiltViewModel()
                        val clearHubViewModel: DashboardViewModel = hiltViewModel()

                        LaunchedEffect(viewModel, clearHubViewModel) {
                            viewModel.onActivationSuccess.collect {
                                isActivated = true
                            }
                        }
                        LaunchedEffect(clearHubViewModel) {
                            clearHubViewModel.onLogoutSuccess.collect {
                                // Restart activity with a fresh intent to clear all ViewModel stores
                                val freshIntent = Intent(this@MainActivity, MainActivity::class.java)
                                finish()
                                startActivity(freshIntent)
                            }
                        }
                        ActivationScreen(viewModel, clearHubViewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            usbConnectionMonitor.suppressAutomatedRequests(30000L)
            usbConnectionMonitor.triggerCheck()
        }
    }
}
