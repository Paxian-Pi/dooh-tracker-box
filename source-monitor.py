import socket
import time
import subprocess
import os
import platform
import threading
import json
import argparse
import sys

# Windows Media SDK Support
HAS_WINSDK = False
if platform.system() == "Windows":
    try:
        import asyncio
        from winsdk.windows.media.control import GlobalSystemMediaTransportControlsSessionManager
        HAS_WINSDK = True
    except ImportError:
        pass

try:
    import customtkinter as ctk
    from tkinter import messagebox
    USE_CUSTOM_TK = True
except Exception as e:
    print(f"[*] Modern UI load failed: {e}")
    import tkinter as tk
    from tkinter import messagebox, scrolledtext
    USE_CUSTOM_TK = False

# Configuration
BROADCAST_PORT = 50006
HEARTBEAT_PORT = 50005
BEAT_INTERVAL = 2  # Seconds

# Path Handling for Bundled Apps
def get_app_data_path():
    if platform.system() == "Darwin":
        base = os.path.expanduser("~/Library/Application Support/DoohMonitor")
    elif platform.system() == "Windows":
        base = os.path.join(os.environ.get("APPDATA", ""), "DoohMonitor")
    else:
        base = os.path.expanduser("~/.dooh_monitor")

    if not os.path.exists(base):
        os.makedirs(base, exist_ok=True)
    return base

DATA_DIR = get_app_data_path()
CONFIG_FILE = os.path.join(DATA_DIR, "monitor_config.json")
LOG_FILE = os.path.join(DATA_DIR, "monitor.log")

if USE_CUSTOM_TK:
    ctk.set_appearance_mode("System")
    ctk.set_default_color_theme("blue")

class SourceMonitorApp:
    def __init__(self, root, initial_id=None):
        self.root = root
        self.is_gui = root is not None
        self.is_running = False
        self.tracker_ip = None
        self.search_start_time = 0
        self.is_hibernating = False 

        if self.is_gui:
            self.root.title("DOOH Source Monitor")
            self.root.geometry("540x500")

            if USE_CUSTOM_TK:
                self.target_id_var = ctk.StringVar()
                self.status_msg_var = ctk.StringVar(value="Status: Stopped")
            else:
                self.target_id_var = tk.StringVar()
                self.status_msg_var = tk.StringVar(value="Status: Stopped")

            try:
                self.root.lift()
                self.root.focus_force()
            except:
                pass

        self.target_id_raw = initial_id or ""

        if self.is_gui:
            self.load_config()
            if initial_id:
                self.target_id_var.set(initial_id)
            self.create_widgets()

        self.log("App initialized. Source platform: " + ("macOS" if platform.system() == "Darwin" else platform.system()))

        # Auto-resume: Start monitoring if an ID is present
        active_id = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
        if active_id:
            self.log(f"Auto-starting with ID: {active_id}")
            if self.is_gui:
                self.root.after(500, self.toggle_monitoring)
            else:
                self.toggle_monitoring()

    def create_widgets(self):
        if USE_CUSTOM_TK:
            self.main_frame = ctk.CTkFrame(self.root, corner_radius=20)
            self.main_frame.pack(pady=25, padx=25, fill="both", expand=True)

            ctk.CTkLabel(self.main_frame, text="Target Tracker ID (Site ID)", font=("Helvetica", 18, "bold")).pack(pady=(25, 5))

            self.entry_id = ctk.CTkEntry(
                self.main_frame,
                textvariable=self.target_id_var,
                height=45,
                placeholder_text="Enter Site ID...",
                corner_radius=22.5,
                justify='center',
                border_width=1,
                font=("Courier", 12.5)
            )
            self.entry_id.pack(pady=10, padx=30, fill="x")

            ctk.CTkLabel(self.main_frame, text="(Required to link a specific tracker)", text_color="gray", font=("Helvetica", 12)).pack()

            self.status_label = ctk.CTkLabel(self.main_frame, textvariable=self.status_msg_var, font=("Courier", 14, "italic"))
            self.status_label.pack(pady=10)

            self.btn_toggle = ctk.CTkButton(
                self.main_frame,
                text="Start Monitoring",
                command=self.toggle_monitoring,
                width=240,
                height=50,
                corner_radius=25,
                font=("Helvetica", 14, "bold"),
                fg_color="#218838",
                hover_color="#297339"
            )
            self.btn_toggle.pack(pady=10, padx=30)

            ctk.CTkLabel(self.main_frame, text="Activity Log", font=("Helvetica", 12, "bold")).pack(anchor="w", padx=35, pady=(15, 0))
            self.log_area = ctk.CTkTextbox(self.main_frame, height=160, corner_radius=15, font=("Courier", 12))
            self.log_area.pack(pady=10, padx=30, fill="x")
            self.log_area.configure(state="disabled")
        else:
            tk.Label(self.root, text="Target Tracker ID (Site ID):", font=('Helvetica', 10, 'bold')).pack(pady=(20, 5))
            self.entry_id = tk.Entry(self.root, textvariable=self.target_id_var, width=30, justify='center')
            self.entry_id.pack(pady=5)
            tk.Label(self.root, textvariable=self.status_msg_var).pack(pady=10)
            self.btn_toggle = tk.Button(self.root, text="Start Monitoring", command=self.toggle_monitoring)
            self.btn_toggle.pack(pady=10)
            self.log_area = scrolledtext.ScrolledText(self.root, height=10, width=55, state='disabled')
            self.log_area.pack(pady=5, padx=10)

    def log(self, message):
        timestamp = time.strftime("%H:%M:%S")
        full_msg = f"[{timestamp}] {message}"
        if self.is_gui:
            self.root.after(0, self._safe_log_update, full_msg)
        else:
            print(full_msg)
        try:
            with open(LOG_FILE, "a") as f:
                f.write(full_msg + "\n")
        except:
            pass

    def _safe_log_update(self, msg):
        self.log_area.configure(state='normal')
        self.log_area.insert("end", msg + "\n")
        self.log_area.see("end")
        self.log_area.configure(state='disabled')

    def update_status(self, msg):
        if self.is_gui:
            self.root.after(0, lambda: self.status_msg_var.set(msg))

    def load_config(self):
        if os.path.exists(CONFIG_FILE):
            try:
                with open(CONFIG_FILE, "r") as f:
                    data = json.load(f)
                    val = data.get("target_id", "")
                    self.target_id_var.set(val)
            except: pass

    def save_config(self):
        current_id = self.target_id_var.get() if self.is_gui else self.target_id_raw
        with open(CONFIG_FILE, "w") as f:
            json.dump({"target_id": current_id}, f)

    def toggle_monitoring(self):
        if not self.is_running:
            active_id = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
            if not active_id:
                if self.is_gui:
                    messagebox.showerror("Error", "Target Tracker ID is required.")
                    self.entry_id.focus_set()
                return

            self.save_config()
            self.tracker_ip = None
            self.search_start_time = time.time()
            self.is_running = True
            self.is_hibernating = False
            if self.is_gui:
                if USE_CUSTOM_TK:
                    self.btn_toggle.configure(text="Searching (30s)", fg_color="#FFBF00", hover_color="#E5AB00", text_color="black")
                    self.entry_id.configure(state='disabled')
                else:
                    self.btn_toggle.config(text="Searching (30s)", bg="#FFBF00")
                    self.entry_id.config(state='disabled')
                self.update_status("Status: Searching for Tracker...")
                self.root.after(100, self._update_smooth_timer)

            self.monitor_thread = threading.Thread(target=self.monitoring_loop, daemon=True)
            self.monitor_thread.start()
            self.log(f"Monitoring started for ID: {active_id}")
        else:
            self.is_running = False
            self.is_hibernating = False
            if self.is_gui:
                if USE_CUSTOM_TK:
                    self.btn_toggle.configure(text="Start Monitoring", fg_color="#218838", hover_color="#297339", text_color="white")
                    self.entry_id.configure(state='normal')
                else:
                    self.btn_toggle.config(text="Start Monitoring", bg="green")
                    self.entry_id.config(state='normal')
                self.update_status("Status: Stopped")
            self.log("Monitoring stopped.")

    def _update_smooth_timer(self):
        if not self.is_running or self.tracker_ip:
            return
        elapsed = time.time() - self.search_start_time
        remaining = int(max(0, 30 - elapsed))
        status = self.status_msg_var.get()
        if "Searching" in status:
            text = f"Resetting ({remaining}s)"
            color = "#FFBF00"
            if USE_CUSTOM_TK:
                self.btn_toggle.configure(text=text, fg_color=color, text_color="black")
            else:
                self.btn_toggle.config(text=text, bg=color)
        if remaining > 0:
            self.root.after(100, self._update_smooth_timer)

    def is_media_playing(self):
        system = platform.system()
        cflags = 0x08000000 if system == "Windows" else 0
        if system == "Windows":
            cmd = 'tasklist /FI "STATUS eq RUNNING"'
            players = ["vlc", "mpc-hc", "chrome", "wmplayer", "mpv", "gst-launch", "magicinfo", "webos"]
        elif system == "Darwin":
            cmd = 'ps -e'
            players = ["vlc", "quicktime player", "iina", "chrome", "mpv", "gst-launch"]
        else:
            cmd = 'ps -e'
            players = ["vlc", "mpv", "chrome", "gst-launch"]
        try:
            output = subprocess.check_output(cmd, stderr=subprocess.STDOUT, creationflags=cflags, timeout=2).decode().lower()
            return any(player in output for player in players)
        except: return True

    def get_active_ports(self):
        """Detects physical display output ports currently active."""
        system = platform.system()
        cflags = 0x08000000 if system == "Windows" else 0
        ports = []
        try:
            if system == "Darwin":
                # 1. Fast check via ioreg (Hardware Registry)
                ioreg_out = ""
                try: ioreg_out = subprocess.check_output(["ioreg", "-n", "AppleDisplay", "-r"], timeout=2).decode().upper()
                except: pass

                # 2. Detailed check via system_profiler
                sp_out = ""
                try: sp_out = subprocess.check_output(["system_profiler", "SPDisplaysDataType"], timeout=3).decode().upper()
                except: pass

                # Look for specific connection signatures
                if "HDMI" in ioreg_out or "HDMI" in sp_out: ports.append("HDMI")
                if "DISPLAYPORT" in ioreg_out or "DISPLAYPORT" in sp_out: ports.append("DP/USB-C")
                if "DVI" in ioreg_out or "DVI" in sp_out: ports.append("DVI")
                if "VGA" in ioreg_out or "VGA" in sp_out: ports.append("VGA")

                # Fallback: Check if multiple displays are "Online" but type is generic
                if not ports and sp_out.count("ONLINE: YES") > 1:
                    ports.append("External Monitor")

            elif system == "Windows":
                # Use a faster, lighter query for Windows
                cmd = "powershell -Command \"Get-WmiObject Win32_PnPEntity | Where-Object { $_.Service -eq 'monitor' } | Select-Object -ExpandProperty Name\""
                output = subprocess.check_output(cmd, shell=True, creationflags=cflags, timeout=3).decode().strip()
                if output:
                    for line in output.split('\n'):
                        if "Generic" not in line or "PnP" in line:
                            ports.append(line[:15].strip())
                if not ports:
                    ports.append("Display Active")
            else:
                # Linux (X11)
                output = subprocess.check_output("xrandr --query", shell=True).decode()
                for line in output.split('\n'):
                    if " connected " in line and "primary" not in line:
                        ports.append(line.split()[0])
        except Exception as e:
            self.log(f"Port detection error: {e}")

        return ",".join(ports) if ports else "Internal/None"

    def check_transmission(self):
        """Checks if the system is actively transmitting video/audio signals."""
        system = platform.system()
        cflags = 0x08000000 if system == "Windows" else 0
        try:
            if system == "Darwin":
                output = subprocess.check_output(["pmset", "-g", "assertions"], timeout=2).decode()
                is_video = any("PreventUserIdleDisplaySleep" in l and " 1" in l for l in output.splitlines())
                is_audio = any("PreventUserIdleSystemSleep" in l and " 1" in l for l in output.splitlines())
                return "YES" if (is_video or is_audio) else "NO"
            elif system == "Windows":
                # Strategy 1: Power Requests (requires admin for some fields)
                output = subprocess.check_output("powercfg /requests", shell=True, creationflags=cflags, timeout=2).decode().upper()
                is_power_video = "DISPLAY:" in output and "NONE" not in output.split("DISPLAY:")[1].split("\n")[1]
                is_power_audio = "AUDIO:" in output and "NONE" not in output.split("AUDIO:")[1].split("\n")[1]

                if is_power_video or is_power_audio:
                    return "YES"

                # Strategy 2: Simple process heuristic for Windows: If a known player is HEALTHY and has CPU usage > 0.5%
                players = ["vlc", "chrome", "msedge", "wmplayer", "mpv", "gst-launch", "magicinfo", "webos"]
                for player in players:
                    check_cmd = f"powershell -Command \"Get-Process {player} -ErrorAction SilentlyContinue | Where-Object {{ $_.CPU -gt 0.5 }} | Select-Object -ExpandProperty Id\""
                    try:
                        res = subprocess.check_output(check_cmd, shell=True, creationflags=cflags, timeout=1).decode().strip()
                        if res: return "YES"
                    except: continue

        except:
            pass
        return "NO"

    def get_current_media_title(self):
        """Attempts to extract the title/filename of the media currently playing from common signage players."""
        system = platform.system()
        cflags = 0x08000000 if system == "Windows" else 0
        try:
            if system == "Darwin":
                # macOS: Improved multi-player detection with explicit variable scoping
                script = '''
                set finalTitle to "NONE"
                try
                    tell application "System Events"
                        set frontProcess to first application process whose frontmost is true
                        set frontName to name of frontProcess
                    end tell
                    
                    if frontName is "VLC" then
                        tell application "VLC" to set finalTitle to name of current item
                    else if frontName is "QuickTime Player" then
                        tell application "QuickTime Player" to set finalTitle to name of document 1
                    else if (frontName contains "Chrome") or (frontName contains "Chromium") then
                        tell application "Google Chrome" to set finalTitle to title of active tab of window 1
                    else if frontName contains "mpv" then
                        tell application "System Events" to tell process frontName to set finalTitle to name of window 1
                    else if frontName contains "Safari" then
                        tell application "Safari" to set finalTitle to name of window 1
                    else
                        -- Check background players via System Events (Passive)
                        tell application "System Events"
                            if exists (application process "VLC") then
                                tell application "VLC" to set finalTitle to name of current item
                            else if exists (application process "mpv") then
                                set finalTitle to name of window 1 of application process "mpv"
                            end if
                            if finalTitle is "NONE" and (exists (application process "QuickTime Player")) then
                                tell application "QuickTime Player" to set finalTitle to name of document 1
                            end if
                        end tell

                        -- Global Fallback: Get the title of the frontmost window (Common for custom signage players)
                        if finalTitle is "NONE" then
                            tell application "System Events"
                                tell frontProcess
                                    if (count of windows) > 0 then set finalTitle to name of window 1
                                end tell
                            end tell
                        end if
                    end if
                on error
                    set finalTitle to "NONE"
                end try
                return finalTitle
                '''
                title = subprocess.check_output(["osascript", "-e", script]).decode().strip()
                # Filter out system UI titles
                ignored = ["", "none", "desktop", "finder", "dock", "loginwindow", "system events", "control center", "siri", "notification center", "screen sharing"]
                if title.lower() in ignored: return "NONE"
                return title if len(title) > 2 else "NONE"

            elif system == "Windows":
                # Windows: Prioritized detection for signage players (mpv, VLC, Chrome, MagicINFO, etc.)
                system_titles = ["task manager", "settings", "microsoft text input", "program manager", "calculator", "microsoft store", "windows input experience", "taskbar", "start"]

                # 1. Direct Foreground Window Check (Best for Signage players in full-screen)
                fg_cmd = 'powershell -Command "$sig = \'[DllImport(\\\"user32.dll\\\", CharSet=CharSet.Auto)] public static extern int GetWindowText(IntPtr hWnd, System.Text.StringBuilder lpString, int nMaxCount); [DllImport(\\\"user32.dll\\\")] public static extern IntPtr GetForegroundWindow();\'; Add-Type -MemberDefinition $sig -Name Win32Utils -Namespace Win32; $hwnd = [Win32.Win32Utils]::GetForegroundWindow(); $sb = New-Object System.Text.StringBuilder 256; $null = [Win32.Win32Utils]::GetWindowText($hwnd, $sb, $sb.Capacity); $sb.ToString()"'
                try:
                    title = subprocess.check_output(fg_cmd, shell=True, creationflags=cflags, timeout=2).decode().strip()
                    if title and len(title) > 3 and title.lower() not in system_titles:
                        # Return if it looks like content (contains extension or isn't just the player name)
                        if any(ext in title.lower() for ext in [".mp4", ".mov", ".avi", ".mkv", ".jpg", ".png", ".webp", ".html"]):
                            return title
                        # If it's MagicINFO or webOS signage, their window title often IS the content or playlist name
                        if any(p in title.lower() for p in ["magicinfo", "webos", "signage", "brightsign", "player"]):
                            return title
                except: pass

                # 2. Modern Media System (Chrome, modern WMP, Spotify)
                smtc_cmd = 'powershell -Command "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null; $m = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync().GetAwaiter().GetResult(); $s = $m.GetCurrentSession(); if ($s) { $p = $s.TryGetMediaPropertiesAsync().GetAwaiter().GetResult(); $p.Title }"'
                try:
                    smtc_title = subprocess.check_output(smtc_cmd, shell=True, creationflags=cflags, timeout=2).decode().strip()
                    if smtc_title and len(smtc_title) > 2:
                        return smtc_title
                except: pass

                # 3. Process Scan Fallback (Detects mpv, vlc, chromium, gst-launch, magicinfo)
                cmd = "powershell -Command \"Get-Process | Where-Object { $_.MainWindowTitle } | Select-Object -ExpandProperty MainWindowTitle\""
                try:
                    output = subprocess.check_output(cmd, shell=True, creationflags=cflags, timeout=3).decode().strip()
                    if output:
                        titles = output.split('\n')
                        media_exts = [".mp4", ".mov", ".avi", ".mkv", ".jpg", ".jpeg", ".png", ".webp", ".html", "index"]
                        # First pass: look for files
                        for t in titles:
                            t = t.strip()
                            if any(ext in t.lower() for ext in media_exts):
                                return t
                        # Second pass: look for players
                        players_keywords = ["mpv", "vlc", "chrome", "chromium", "gst-launch", "magicinfo", "webos", "brightsign"]
                        for t in titles:
                            t = t.strip()
                            if any(k in t.lower() for k in players_keywords) and len(t) > 3:
                                return t
                except: pass
        except:
            pass
        return "NONE"

    def monitoring_loop(self):
        while self.is_running:
            try:
                found_ip = self.find_tracker_ip()
                site_id = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
                if found_ip:
                    if self.tracker_ip != found_ip:
                        self.log(f"🔄 Tracker IP for {site_id} changed or rediscovered: {found_ip}")
                        self.tracker_ip = found_ip
                    self.search_start_time = time.time()

                elapsed = time.time() - self.search_start_time
                if elapsed > 30:
                    self.log(f"⚠️ Tracker not found for {site_id}. Resetting...")
                    self.tracker_ip = None
                    if self.is_gui: self.root.after(0, self._start_hibernation)
                    else: self.is_running = False
                    return

                if not self.tracker_ip:
                    self.update_status("Status: Searching for Tracker...")
                    time.sleep(1)
                    continue

                status = "HEALTHY" if self.is_media_playing() else "NO_MEDIA"
                active_ports = self.get_active_ports()
                transmitting = self.check_transmission()
                media_title = self.get_current_media_title()

                # Sanitize media title for network transport (remove colons)
                media_title = media_title.replace(":", "-").replace("\n", " ").strip()

                if active_ports == "Internal/None": transmitting = "NO"
                
                source_time = int(time.time() * 1000)
                # Protocol: PULSE:STATUS:HOSTNAME:PORTS:TIMESTAMP:TRANSMITTING:MEDIA_TITLE
                message = f"PULSE:{status}:{platform.node()}:{active_ports}:{source_time}:{transmitting}:{media_title}"

                with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                    s.settimeout(2.0)
                    s.sendto(message.encode(), (self.tracker_ip, HEARTBEAT_PORT))
                    self.update_status(f"Status: Connected to {self.tracker_ip}")

                    if self.is_gui:
                        self.root.after(0, lambda: self.btn_toggle.configure(text="Stop Monitoring", fg_color="#dc3545", hover_color="#c82333", text_color="white") if USE_CUSTOM_TK else self.btn_toggle.config(text="Stop Monitoring", bg="red"))

                    self.search_start_time = time.time()

            except Exception as e:
                self.log(f"Connection error: {e}")
                self.tracker_ip = None
                self.update_status("Status: Connection Lost. Retrying...")
                if self.is_gui: self.root.after(0, self._update_smooth_timer)
            time.sleep(BEAT_INTERVAL)

    def find_tracker_ip(self):
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.bind(('', BROADCAST_PORT))
            s.settimeout(0.5)
            try:
                data, addr = s.recvfrom(1024)
                parts = data.decode().split(":")
                if parts[0] == "DOOH_TRACKER_ADVERT":
                    found_id = parts[2] if len(parts) > 2 else "UNKNOWN"
                    current_owner = parts[3] if len(parts) > 3 else "NONE"
                    target = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
                    if target.lower() == found_id.lower():
                        my_name = platform.node()
                        if current_owner != "NONE" and current_owner != my_name:
                            self.update_status(f"Status: Site ID in use by {current_owner}")
                            return None
                        return addr[0]
            except: pass
            return None

    def _start_hibernation(self):
        if self.is_running: self.toggle_monitoring()
        self.is_hibernating = True
        self.update_status("Status: Offline (Auto-resume active)")
        self.log("Waiting for network/tracker recovery... 💤")
        threading.Thread(target=self._hibernation_watchdog, daemon=True).start()

    def _hibernation_watchdog(self):
        while self.is_hibernating and not self.is_running:
            ip = self.find_tracker_ip()
            if ip:
                self.log("✨ Network recovered! Resuming...")
                self.is_hibernating = False
                if self.is_gui: self.root.after(0, self.toggle_monitoring)
                return
            time.sleep(5)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="DOOH Source Monitor")
    parser.add_argument("--id", help="Target Tracker Site ID")
    parser.add_argument("--cli", action="store_true", help="Run in CLI mode")
    args = parser.parse_args()
    if not args.cli:
        try:
            root = ctk.CTk() if USE_CUSTOM_TK else tk.Tk()
            if platform.system() == "Darwin":
                subprocess.Popen(['osascript', '-e', f'tell application "System Events" to set frontmost of the first process whose unix id is {os.getpid()} to true'])
            app = SourceMonitorApp(root, initial_id=args.id)
            root.mainloop()
        except: args.cli = True
    if args.cli:
        app = SourceMonitorApp(None, initial_id=args.id)
        try:
            while True: time.sleep(1)
        except KeyboardInterrupt: sys.exit(0)
