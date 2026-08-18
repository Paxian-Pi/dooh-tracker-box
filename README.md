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
*   **Standardized ID Format**: Uses a mandatory `#Retin-` prefix for all Site IDs, enforcing a clean 4-digit numeric suffix for technician efficiency.
*   **Instant Visibility**: Registered Site IDs appear on the dashboard instantly on launch, with independent status monitoring for each.

### 2. Autonomous Tracking Engine
*   **Content-Aware Verification**: Automatically detects the filename/title of the media currently playing on the physical billboard via a linked Source Monitor.
*   **Targeted Matching**: Strictly matches campaigns based on **Site ID + Title**, ensuring hardware isolation and preventing data cross-talk.
*   **Immediate Audit Trail**: Logs playback events to the local database the exact millisecond a campaign finishes, providing real-time visual feedback in the UI.
*   **Real-Time Reporting**: Automatically triggers `sendProofOfPlay` and `trackHourly` cumulative counts immediately after each matched campaign completion.

### 3. Source Monitor Integration 🖥️
*   **Cross-Platform Monitor**: A Python-based client (`source-monitor.py`) with a modern UI (CustomTkinter) for the media source machine.
*   **Auto-Resume Orchestrator**: Automatically restores all previously active Site IDs and their Manual IP settings upon machine restart or app relaunch.
*   **Hardware-Locked Affinity**: Uses unique Device IDs to prevent "flipping" between duplicate Site IDs on different devices.

### 4. Hybrid Media Monitoring 🔄
*   **External Mode**: Tracks campaigns from a networked source machine (Windows/Mac) via UDP pulses.
*   **Internal Mode**: Tracks campaigns from a media player running on the **same Android device** using OS-level `MediaSession` metadata scraping.
*   **Optimized Video Verification**: Skips large video downloads to speed up site onboarding; uses title-based metadata matching with a high-contrast green **"CAMPAIGN VERIFIED"** status for visual audit.

---

## 📺 Technician Dashboard

The dashboard is fully optimized for **Android TV Box** (Landscape, D-pad navigation) and **Android Mobile** devices:

*   **High-Contrast TV Focus**: A crisp **1.dp Cyan selection frame** follows the remote across every card, button, and toggle for clear visual feedback.
*   **Dynamic Mirroring**:
    *   **Images**: Renders a live visual mirror of the physical billboard.
    *   **Videos**: Displays a dark green **"CAMPAIGN VERIFIED"** screen to ensure 100% hardware stability on TV Box decoders.
*   **Truth Status Colors**:
    *   🟢 **Active**: Billboard is playing matched/trusted content.
    *   🟠 **Content Mismatch**: Billboard is playing unrecognized media (Tracking Suspended).
    *   🔵 **No Media Detected**: Source machine is linked but player is closed/idle.

---

## 🚀 Getting Started

### Installation
1.  **Android Tracker**: Build and deploy the `:app` module.
2.  **Activation**: Enter the 4-digit Site ID (prefix `#Retin-` is automatic).
3.  **Source Monitor**: Run `source-monitor.py` on the billboard machine. It will automatically detect the Tracker on the same subnet.

---

## 📋 Monitoring & Debugging

Filter Logcat by the following tags:
*   `Tracking`: Real-time session transitions, Audit Trail logs, and Cloud Sync status.
*   `Sync`: Campaign reference fetching and image download status.
*   `SignalMonitor`: Heartbeat reception and Site ID ownership status.

---
**Verified for production Multi-Identity deployment.** ✅
