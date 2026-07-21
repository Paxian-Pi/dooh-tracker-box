# DOOH Tracker Box 🚀

An autonomous, production-grade verification tracker for Android TV Boxes. This system validates scheduled Digital Out-Of-Home (DOOH) campaigns against a physical HDMI stream using USB/UVC hardware detection.

## 🏗 Project Architecture

The project follows a robust **Multi-Module Clean Architecture** pattern to ensure separation of concerns and background stability:

*   **`:app`**: Jetpack Compose for TV (Material 3) user interface, ViewModels, and UI logic.
*   **`:data`**: Core business logic, Room database persistence, Retrofit network layer, and Hardware/System monitors (USB, HDMI, Location, Health).
*   **`:tracker`**: A foreground service that manages the 24/7 tracking loop and autonomous campaign synchronization.

---

## ✨ Key Features

### 1. Autonomous Tracking Engine
*   **Minute-by-Minute Verification**: Automatically sends "Proof of Play" (PoP) pings every 60 seconds for every active campaign.
*   **Hourly Summaries**: Sends a high-level summary (`trackHourly`) at the start of every hour, including numeric weekday mapping and incremental play counts.
*   **Window Awareness**: Uses a `VerificationGatekeeper` to ensure tracking only occurs when the system clock matches the campaign schedule.

### 2. Intelligent Hardware Monitoring
*   **HDMI Signal Detection**: Detects HDMI Capture Cards as USB Video Class (UVC) devices.
*   **VID:PID Binding**: Hardcoded hardware binding ensures the app "remembers" authorized capture cards across reboots, providing a zero-touch experience after the first setup.
*   **Handshake Stabilization**: Industrial-grade locking (Mutex) and 10-second OS synchronization guards prevent duplicate USB permission dialogs.

### 3. Smart Location & System Health
*   **Triple-Layer Location**: 
    1.  **High-Accuracy**: GPS/WiFi scanning.
    2.  **IP-Fallback**: Geo-location via public IP (for Ethernet-only boxes).
    3.  **Manual Overwrite**: Automatically uses installation notes if no digital signal is found.
*   **System Health Dashboard**: Real-time display of RAM usage, Storage % (Used/Total), System Uptime, and Internal IP address.

### 4. Secure Technician Activation
*   **Hardware Binding**: Devices must be activated with a code and username that matches the backend `MediaResult` assigned to that specific site.
*   **Installation Metadata**: Technicians provide installation notes (e.g., "Gate 4, Terminal 1") which are used as persistent metadata for all verification logs.

---

## 📺 Technician Dashboard

The dashboard is optimized for **Android TV Box** displays (Landscape, D-pad navigation):

*   **2-Column Grid**: Core metrics (HDMI, Internet, IP, Sync) are visible at a single glance.
*   **Horizontal Marquee**: Current campaign titles automatically scroll to handle multiple overlapping schedules.
*   **3-Column Audit Trail**: "Recent Logs" view showing Campaign Title, Date, and precise Time/Sync status.
*   **High-Contrast Dark Theme**: Deep charcoal surfaces (`#0F111A`) with neon status indicators (Green, Red, Cyan, Yellow) for visibility in all lighting conditions.

---

## 🛠 Tech Stack

*   **Language**: Kotlin
*   **UI**: Jetpack Compose for TV (Material 3)
*   **Dependency Injection**: Hilt
*   **Database**: Room (with multi-version migration support)
*   **Networking**: Retrofit 2 + OkHttp (with automated Bearer Token injection)
*   **Async**: Kotlin Coroutines & Flow
*   **Background Tasks**: WorkManager (for offline log synchronization)

---

## 🚀 Getting Started

### Prerequisites
*   Android TV Box or Android Mobile (v8.0+)
*   USB HDMI Capture Card (UVC Compatible)
*   Active Internet Connection (WiFi or Ethernet)

### Installation
1.  Clone the repository.
2.  Open in **Android Studio Ladybug** or newer.
3.  Build and deploy the `:app` module.
4.  **First Launch**: Authorize the USB device and grant Location/Camera/Audio permissions.
5.  **Activation**: Enter the activation code provided by the Retitrak administrator.

---

## 📋 Monitoring & Debugging

Filter Logcat by the following tags to monitor system behavior:
*   `Tracking`: Real-time Proof of Play and Hourly payloads.
*   `USB`: Hardware handshake and permission logs.
*   `Sync`: Background campaign download status.
*   `Location`: Geocoding and IP-fallback resolution details.

---

## 🌐 API Configuration
The system is configured to communicate with:
*   **Base URL**: `https://demo.retitrak.africa/api/mobile/`
*   **Auth**: Automated Static Bearer Token injection via `OkHttp Interceptor`.

---
**Verified for production deployment.** ✅
