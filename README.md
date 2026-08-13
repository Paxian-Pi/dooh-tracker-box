# DOOH Tracker Box 🚀

An autonomous, production-grade verification tracker for Android TV Boxes. This system validates scheduled Digital Out-Of-Home (DOOH) campaigns against actual physical playback using a networked **Source Monitor** that provides content-level verification.

## 🏗 Project Architecture

The project follows a robust **Multi-Module Clean Architecture** pattern to ensure separation of concerns and background stability:

*   **`:app`**: Jetpack Compose for TV (Material 3) user interface, ViewModels, and UI logic.
*   **`:data`**: Core business logic, Room database persistence, Retrofit network layer, and Hardware/System monitors (Network Heartbeat, Location, Health).
*   **`:tracker`**: A foreground service that manages the 24/7 tracking loop and autonomous campaign synchronization.

---

## ✨ Key Features

### 1. Multi-Identity Hub 🏢
*   **Simultaneous Multi-Tenant Tracking**: Capable of tracking, verifying, and mirroring rotations for multiple clients (Site IDs) on a single physical billboard.
*   **Instant Visibility**: Registered Site IDs appear on the dashboard instantly on launch, with independent status monitoring for each.
*   **Auto-Resume Orchestrator**: The Source Monitor automatically spawns independent instances for all previously active sites on system reboot.

### 2. Autonomous Tracking Engine
*   **Content-Aware Verification**: Automatically detects the filename/title of the media currently playing on the physical billboard via a linked Source Monitor.
*   **Targeted Matching**: Strictly matches campaigns based on **Site ID + Title**, ensuring hardware isolation and preventing data cross-talk.
*   **Rotation Discovery**: Automatically detects ads removed or re-injected. Ads are automatically removed from the active list if not seen for 15 minutes.
*   **Compliance Verification**: If a mismatch is detected, tracking is suspended and flagged in the audit trail.

### 3. Source Monitor Integration 🖥️
*   **Cross-Platform Monitor**: A Python-based client (`source-monitor.py`) with a modern UI (CustomTkinter) for the media source machine.
*   **Hardware-Locked Affinity**: Uses unique Device IDs to prevent "flipping" between duplicate Site IDs on different devices.
*   **Manual IP Override**: Supports direct connection to specific tracker IPs for restricted network environments, with strict **Same-Network Validation** to ensure the device is on the same local connection (WiFi/Ethernet).
*   **Zero-Persistence Resets**: Starting with just an ID flag automatically clears manual overrides, ensuring Auto-Discovery remains the default reliable flow.

### 4. Smart Location & System Health
*   **Triple-Layer Location**: GPS/WiFi, IP-Fallback, and Manual Metadata overwrite.
*   **System Health Dashboard**: Real-time display of RAM usage, Storage % (Used/Total), and System Uptime.

---

## 📺 Technician Dashboard

The dashboard is fully optimized for **Android TV Box** (Landscape, D-pad navigation) and **Android Mobile** devices:

*   **High-Contrast TV Focus**: A crisp **1.dp Cyan selection frame** follows the remote across every card, button, and toggle for clear visual feedback.
*   **Hybrid Interaction**: Seamlessly handles both D-pad remote navigation and high-accuracy mobile touch events.
*   **Dynamic Mirroring**: Preview window displays "Campaign Reference" status during sync and switches to live Video/Image Mirroring only when all site data is ready.
*   **Night/Day Mode**: Persistent theme toggle to ensure maximum visibility in both bright day environments and late-night maintenance conditions.
*   **Truth Status Colors**:
    *   🟢 **Active**: Billboard is playing matched/trusted content.
    *   🟠 **Content Mismatch**: Billboard is playing unrecognized media (Tracking Suspended).
    *   🔵 **No Media Detected**: Source machine is linked but player is closed/idle.

---

## 🛠 Tech Stack

*   **Language**: Kotlin (Android) / Python (Source Monitor)
*   **UI**: Jetpack Compose for TV (Material 3) / CustomTkinter (Python)
*   **Database**: Room (with multi-version migration support)
*   **Networking**: Retrofit 2 + UDP (Security-locked discovery)
*   **Async**: Kotlin Coroutines & Flow
*   **Background Tasks**: WorkManager (for offline log synchronization)

---

## 🚀 Getting Started

### Prerequisites
*   Android TV Box or Android Mobile (v8.0+)
*   Source Monitor Client (Mac or Windows machine connected to the billboard)
*   Local Network Connection (Tracker and Monitor must be on the same WiFi/Subnet)

### Installation
1. Clone the repository.
2. **Android Tracker**: Build and deploy the `:app` module to the device.
3. **Activation**: Enter the site/activation code on the app to register a client.
4. **Source Monitor**: Run `source-monitor.py` on the billboard machine:
    `python3 source-monitor.py --id YOUR_SITE_ID`
    *Optional*: Use `--ip 192.168.x.x` for manual override.

---

## 📋 Monitoring & Debugging

Filter Logcat by the following tags:
*   `Tracking`: Real-time Proof of Play and Hourly payloads.
*   `HeartbeatServer`: Pulse routing and security filtering.
*   `Sync`: Background campaign download and local path resolution.

---
**Verified for production Multi-Identity deployment.** ✅
