# DOOH Tracker Box 🚀

An autonomous, production-grade verification tracker for Android TV Boxes. This system validates scheduled Digital Out-Of-Home (DOOH) campaigns against actual physical playback using a networked **Source Monitor** that provides content-level verification.

## 🏗 Project Architecture

The project follows a robust **Multi-Module Clean Architecture** pattern to ensure separation of concerns and background stability:

*   **`:app`**: Jetpack Compose for TV (Material 3) user interface, ViewModels, and UI logic.
*   **`:data`**: Core business logic, Room database persistence, Retrofit network layer, and Hardware/System monitors (Network Heartbeat, Location, Health).
*   **`:tracker`**: A foreground service that manages the 24/7 tracking loop and autonomous campaign synchronization.

---

## ✨ Key Features

### 1. Autonomous Tracking Engine
*   **Content-Aware Verification**: Automatically detects the filename/title of the media currently playing on the physical billboard via a linked Source Monitor.
*   **Per-Ad Compliance**: Independently validates each scheduled campaign against the live billboard rotation. If a mismatch is detected, tracking for that specific ad is suspended and flagged in the audit trail.
*   **Rotation Discovery**: Automatically detects ads removed or re-injected by the media owner. Ads missing from rotation for >20 minutes are temporarily suspended from the tracker.
*   **Hourly Summaries**: Sends a high-level summary (`trackHourly`) at the start of every hour, including numeric weekday mapping and incremental play counts.

### 2. Source Monitor Integration
*   **Cross-Platform Monitor**: A Python-based client (`source-monitor.py`) runs on the media source machine (Windows/Mac) and broadcasts its state to the Android tracker.
*   **Truth-at-Source Protocol**: Sends a secure UDP Heartbeat (Pulse) containing machine status, hostname, and the **Actual Media Filename** currently being rendered.
*   **Ownership Lock**: The tracker binds to a specific Source machine to prevent unauthorized signal hijacking.

### 3. Smart Location & System Health
*   **Triple-Layer Location**: 
    1.  **High-Accuracy**: GPS/WiFi scanning.
    2.  **IP-Fallback**: Geo-location via public IP (for Ethernet-only boxes).
    3.  **Manual Overwrite**: Automatically uses installation notes if no digital signal is found.
*   **System Health Dashboard**: Real-time display of RAM usage, Storage % (Used/Total), and System Uptime.

### 4. Secure Technician Activation
*   **Hardware Binding**: Devices must be activated with a code and username that matches the backend `MediaResult` assigned to that specific site.
*   **Installation Metadata**: Technicians provide installation notes (e.g., "Gate 4, Terminal 1") which are used as persistent metadata for all verification logs.

---

## 📺 Technician Dashboard

The dashboard is optimized for **Android TV Box** displays (Landscape, D-pad navigation):

*   **Live Preview Mirror**: The preview card on the dashboard acts as a real-time mirror of the physical billboard. It identifies the media currently being rendered by the source machine and displays the corresponding local media file.
*   **2-Column Grid**: Core metrics (Source Monitor, Internet, IP, Sync) are visible at a single glance.
*   **Truth Status Colors**:
    *   🟢 **Active**: Billboard is playing matched/trusted content.
    *   🟠 **Content Mismatch**: Billboard is playing unrecognized media (Tracking Suspended).
    *   🔵 **No Media Detected**: Source machine is linked but player is closed/idle.
*   **3-Column Audit Trail**: "Recent Logs" view showing:
    *   `✓` : Successfully synced pulse.
    *   `☁` : Local pulse pending upload.
    *   `❌` : Compliance rejection (Content Mismatch).

---

## 🛠 Tech Stack

*   **Language**: Kotlin (Android) / Python (Source Monitor)
*   **UI**: Jetpack Compose for TV (Material 3)
*   **Database**: Room (with multi-version migration support)
*   **Networking**: Retrofit 2 + UDP (for local discovery)
*   **Async**: Kotlin Coroutines & Flow
*   **Background Tasks**: WorkManager (for offline log synchronization)

---

## 🚀 Getting Started

### Prerequisites
*   Android TV Box or Android Mobile (v8.0+)
*   Source Monitor Client (Mac or Windows machine connected to the billboard)
*   Local Network Connection (Tracker and Monitor must be on the same subnet)

### Installation
1. Clone the repository.
2. **Android Tracker**: Build and deploy the `:app` module to the TV Box.
3. **Activation**: Enter the site/activation code on the Android app to link the systems.
4. **Source Monitor**: Run `source-monitor.py` on the billboard machine:
    `python3 source-monitor.py --id YOUR_SITE_ID`
5. **Activation**: Enter the site code on the Android app to link the systems.

---

## 📋 Monitoring & Debugging

Filter Logcat by the following tags:
*   `Tracking`: Real-time Proof of Play and Hourly payloads.
*   `HeartbeatServer`: Local network pulse reception and parsing.
*   `Sync`: Background campaign download status.

---
**Verified for production deployment.** ✅
