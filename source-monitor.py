import socket
import time
import subprocess
import os
import platform
import threading
import json
import argparse
import sys
import ipaddress
import traceback

# In a PyInstaller bundle, sys.executable is the monitor EXE itself.  Do not
# append __file__ when starting a child monitor; it points into the temporary
# one-file extraction directory and becomes an unwanted argparse argument.
IS_FROZEN = getattr(sys, "frozen", False)

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
IDENTITIES_FILE = os.path.join(DATA_DIR, "active_identities.json")
STARTUP_CRASH_LOG = os.path.join(DATA_DIR, "startup-crash.log")

def write_startup_error(context, exc_info=None):
    """Persist startup failures because --windowed deliberately has no console."""
    try:
        details = "".join(traceback.format_exception(*exc_info)) if exc_info else ""
        with open(STARTUP_CRASH_LOG, "a", encoding="utf-8") as f:
            f.write(
                "\n" + "=" * 72 + "\n"
                + time.strftime("%Y-%m-%d %H:%M:%S")
                + f" | {context}\n{details}"
            )
    except Exception:
        pass

def child_monitor_command(site_id, manual_ip=""):
    """Return a source-compatible or PyInstaller-compatible child command."""
    args = ["--id", site_id]
    if manual_ip:
        args.extend(["--ip", manual_ip])

    if IS_FROZEN:
        return [sys.executable, *args]
    return [sys.executable, os.path.abspath(__file__), *args]

def child_creation_flags():
    # A child has its own GUI window; it must not create a black console window.
    return subprocess.CREATE_NO_WINDOW if platform.system() == "Windows" else 0

def child_environment():
    """Make an auto-resumed frozen monitor a new, independent one-file app."""
    env = os.environ.copy()
    if IS_FROZEN:
        # PyInstaller 6.9+: without this, the child reuses the parent's _MEI
        # directory even though the parent exits immediately after spawning it.
        env["PYINSTALLER_RESET_ENVIRONMENT"] = "1"
    return env

if USE_CUSTOM_TK:
    try:
        ctk.set_appearance_mode("System")
        ctk.set_default_color_theme("blue")
    except:
        print("[*] Theme initialization failed. Using high-compatibility mode.")

class SourceMonitorApp:
    def __init__(self, root, initial_id=None, initial_ip=None):
        self.root = root
        self.is_gui = root is not None
        self.is_running = False
        self.tracker_ip = None
        self.search_start_time = 0
        self.is_hibernating = False

        if self.is_gui:
            title = "DOOH Source Monitor"
            if initial_id: title += f" - {initial_id}"
            self.root.title(title)
            self.root.geometry("540x680")

            if USE_CUSTOM_TK:
                self.target_id_var = ctk.StringVar(value="#Retin-")
                self.manual_ip_var = ctk.StringVar()
                self.status_msg_var = ctk.StringVar(value="Status: Stopped")
                self.host_var = ctk.StringVar(value=f"Source Host: {platform.node()}")
                self.port_var = ctk.StringVar(value="Active Port: Detecting...")
                self.stream_var = ctk.StringVar(value="Media Stream: No")
            else:
                self.target_id_var = tk.StringVar(value="#Retin-")
                self.manual_ip_var = tk.StringVar()
                self.status_msg_var = tk.StringVar(value="Status: Stopped")
                self.host_var = tk.StringVar(value=f"Source Host: {platform.node()}")
                self.port_var = tk.StringVar(value="Active Port: Detecting...")
                self.stream_var = tk.StringVar(value="Media Stream: No")

            # Enforce prefix and character limit
            self.target_id_var.trace_add("write", self._on_id_change)

            try:
                self.root.lift()
                self.root.focus_force()
            except:
                pass

        self.target_id_raw = initial_id or ""
        self.manual_ip_raw = initial_ip or ""

        if self.is_gui:
            self.load_config()
            # Prioritize CLI arguments over saved config
            if initial_id:
                if not initial_id.startswith("#Retin-"):
                    initial_id = f"#Retin-{initial_id}"
                self.target_id_var.set(initial_id)
                self.manual_ip_var.set(initial_ip if initial_ip else "")
            elif initial_ip:
                self.manual_ip_var.set(initial_ip)

            self.create_widgets()

        self.log("App initialized. Source platform: " + ("macOS" if platform.system() == "Darwin" else platform.system()))

        # Auto-resume: Start monitoring if an ID is present
        active_id = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
        if active_id and active_id != "#Retin-":
            self.log(f"Auto-starting with ID: {active_id}")
            if self.is_gui:
                self.root.after(500, self.toggle_monitoring)
            else:
                self.toggle_monitoring()

    def _on_id_change(self, *args):
        val = self.target_id_var.get()
        prefix = "#Retin-"
        if not val.startswith(prefix):
            self.target_id_var.set(prefix)
            return

        suffix = val[len(prefix):]
        if len(suffix) > 4:
            self.target_id_var.set(prefix + suffix[:4])

    def create_widgets(self):
        if USE_CUSTOM_TK:
            self.main_frame = ctk.CTkFrame(self.root, corner_radius=20)
            self.main_frame.pack(pady=25, padx=25, fill="both", expand=True)

            ctk.CTkLabel(self.main_frame, text="Target Tracker ID (Site ID)", font=("Helvetica", 18, "bold")).pack(pady=(25, 5))

            self.entry_id = ctk.CTkEntry(
                self.main_frame,
                textvariable=self.target_id_var,
                height=45,
                placeholder_text="e.g. 1234",
                corner_radius=22,
                justify='center',
                border_width=1,
                font=("Courier", 12)
            )
            self.entry_id.pack(pady=10, padx=30, fill="x")

            ctk.CTkLabel(self.main_frame, text="Manual Tracker IP (Optional)", font=("Helvetica", 14, "bold")).pack(pady=(10, 5))
            self.entry_ip = ctk.CTkEntry(
                self.main_frame,
                textvariable=self.manual_ip_var,
                height=35,
                placeholder_text="e.g. 192.168.1.100",
                corner_radius=17,
                justify='center',
                border_width=1,
                font=("Courier", 12)
            )
            self.entry_ip.pack(pady=5, padx=60, fill="x")

            ctk.CTkLabel(self.main_frame, text="(Leave blank for auto-discovery)", text_color="gray", font=("Helvetica", 11)).pack()

            # Telemetry Info
            ctk.CTkLabel(self.main_frame, textvariable=self.host_var, font=("Courier", 12)).pack(pady=(10, 0))
            ctk.CTkLabel(self.main_frame, textvariable=self.port_var, font=("Courier", 12)).pack()
            ctk.CTkLabel(self.main_frame, textvariable=self.stream_var, font=("Courier", 12)).pack()

            self.status_label = ctk.CTkLabel(self.main_frame, textvariable=self.status_msg_var, font=("Courier", 14, "italic"), text_color="#919A73")
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
            tk.Label(self.root, text="(Prefix #Retin- is constant)", font=('Helvetica', 8)).pack()

            tk.Label(self.root, text="Manual Tracker IP (Optional):").pack()
            self.entry_ip = tk.Entry(self.root, textvariable=self.manual_ip_var, width=30, justify='center')
            self.entry_ip.pack(pady=5)

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
                    if val and not val.startswith("#Retin-"):
                        val = f"#Retin-{val}"
                    self.target_id_var.set(val or "#Retin-")
                    self.manual_ip_var.set(data.get("manual_ip", ""))
            except: pass

    def save_config(self):
        current_id = self.target_id_var.get() if self.is_gui else self.target_id_raw
        current_ip = self.manual_ip_var.get() if self.is_gui else self.manual_ip_raw
        with open(CONFIG_FILE, "w") as f:
            json.dump({"target_id": current_id, "manual_ip": current_ip}, f)

    def _update_active_identities(self, site_id, active, manual_ip=""):
        try:
            site_id = site_id.strip()
            if not site_id: return
            identities = {}
            if os.path.exists(IDENTITIES_FILE):
                with open(IDENTITIES_FILE, "r") as f:
                    try:
                        data = json.load(f)
                        # Migration logic for old list-based active_ids
                        if "active_ids" in data and "identities" not in data:
                            for sid in data["active_ids"]:
                                identities[sid] = {"manual_ip": ""}
                        else:
                            identities = data.get("identities", {})
                    except: pass

            if active:
                identities[site_id] = {"manual_ip": manual_ip}
            else:
                if site_id in identities:
                    del identities[site_id]

            with open(IDENTITIES_FILE, "w") as f:
                json.dump({"identities": identities}, f)
        except: pass

    def toggle_monitoring(self):
        active_id = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
        manual_ip = (self.manual_ip_var.get() if self.is_gui else self.manual_ip_raw).strip()

        if not self.is_running:
            if not active_id or active_id == "#Retin-":
                if self.is_gui:
                    messagebox.showerror("Error", "Target Tracker ID is required.")
                    self.entry_id.focus_set()
                return

            if manual_ip:
                if not self._is_valid_local_network_ip(manual_ip):
                    if self.is_gui:
                        messagebox.showerror("Network Error", f"The IP {manual_ip} is not on the same local network (WiFi) as this computer.\n\nManual overrides must be on the same connection for security.")
                    else:
                        self.log(f"CRITICAL: IP {manual_ip} is rejected (Not on same local network).")
                    return

            self.save_config()
            self._update_active_identities(active_id, True, manual_ip)
            self.tracker_ip = None
            self.search_start_time = time.time()
            self.is_running = True
            self.is_hibernating = False
            if self.is_gui:
                if USE_CUSTOM_TK:
                    self.btn_toggle.configure(text="Searching (30s)", fg_color="#FFBF00", hover_color="#E5AB00", text_color="black")
                    self.entry_id.configure(state='disabled')
                    self.entry_ip.configure(state='disabled')
                else:
                    self.btn_toggle.config(text="Searching (30s)", bg="#FFBF00")
                    self.entry_id.config(state='disabled')
                    self.entry_ip.config(state='disabled')
                self.update_status("Status: Searching for Tracker...")
                self.root.after(100, self._update_smooth_timer)

            self.monitor_thread = threading.Thread(target=self.monitoring_loop, daemon=True)
            self.monitor_thread.start()
            self.log(f"Monitoring started for ID: {active_id}")
        else:
            self.is_running = False
            self.is_hibernating = False
            self._update_active_identities(active_id, False)
            if self.is_gui:
                if USE_CUSTOM_TK:
                    self.btn_toggle.configure(text="Start Monitoring", fg_color="#218838", hover_color="#297339", text_color="white")
                    self.entry_id.configure(state='normal')
                    self.entry_ip.configure(state='normal')
                else:
                    self.btn_toggle.config(text="Start Monitoring", bg="green")
                    self.entry_id.config(state='normal')
                    self.entry_ip.config(state='normal')
                self.update_status("Status: Stopped")
            self.log("Monitoring stopped.")

    def _is_valid_local_network_ip(self, ip):
        """Strict validation to ensure the IP is on the SAME local network as this machine."""
        try:
            # 1. Get host's own local IP
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            try:
                s.connect(('10.255.255.255', 1))
                local_ip = s.getsockname()[0]
            except:
                local_ip = '127.0.0.1'
            finally:
                s.close()

            if local_ip == '127.0.0.1' and ip != '127.0.0.1':
                return False

                # 2. Compare Subnets (First 3 octets /24)
            local_prefix = ".".join(local_ip.split('.')[:3])
            target_prefix = ".".join(ip.split('.')[:3])

            if local_prefix != target_prefix:
                self.log(f"Rejected IP {ip}: Not on same subnet as machine ({local_ip})")
                return False

            return True
        except:
            return False

    def _update_smooth_timer(self):
        if not self.is_running or self.tracker_ip:
            return
        elapsed = time.time() - self.search_start_time
        remaining = int(max(0, 30 - elapsed))
        status = self.status_msg_var.get()
        if "Searching" in status:
            text = f"Resetting in ({remaining}s)"
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
        system = platform.system()
        cflags = 0x08000000 if system == "Windows" else 0
        ports = []
        try:
            if system == "Darwin":
                ioreg_out = ""
                try: ioreg_out = subprocess.check_output(["ioreg", "-n", "AppleDisplay", "-r"], timeout=2).decode().upper()
                except: pass
                sp_out = ""
                try: sp_out = subprocess.check_output(["system_profiler", "SPDisplaysDataType"], timeout=3).decode().upper()
                except: pass
                if "HDMI" in ioreg_out or "HDMI" in sp_out: ports.append("HDMI")
                if "DISPLAYPORT" in ioreg_out or "DISPLAYPORT" in sp_out: ports.append("DP/USB-C")
                if "DVI" in ioreg_out or "DVI" in sp_out: ports.append("DVI")
                if "VGA" in ioreg_out or "VGA" in sp_out: ports.append("VGA")
                if not ports and sp_out.count("ONLINE: YES") > 1:
                    ports.append("External Monitor")
            elif system == "Windows":
                cmd = "powershell -Command \"Get-WmiObject Win32_PnPEntity | Where-Object { $_.Service -eq 'monitor' } | Select-Object -ExpandProperty Name\""
                output = subprocess.check_output(cmd, shell=True, creationflags=cflags, timeout=3).decode().strip()
                if output:
                    for line in output.split('\n'):
                        if "Generic" not in line or "PnP" in line:
                            ports.append(line[:15].strip())
                if not ports:
                    ports.append("Display Active")
            else:
                output = subprocess.check_output("xrandr --query", shell=True).decode()
                for line in output.split('\n'):
                    if " connected " in line and "primary" not in line:
                        ports.append(line.split()[0])
        except Exception as e:
            self.log(f"Port detection error: {e}")
        return ",".join(ports) if ports else "Internal/None"

    def check_transmission(self):
        system = platform.system()
        cflags = 0x08000000 if system == "Windows" else 0
        try:
            if system == "Darwin":
                output = subprocess.check_output(["pmset", "-g", "assertions"], timeout=2).decode()
                is_video = any("PreventUserIdleDisplaySleep" in l and " 1" in l for l in output.splitlines())
                is_audio = any("PreventUserIdleSystemSleep" in l and " 1" in l for l in output.splitlines())
                return "YES" if (is_video or is_audio) else "NO"
            elif system == "Windows":
                output = subprocess.check_output("powercfg /requests", shell=True, creationflags=cflags, timeout=2).decode().upper()
                is_power_video = "DISPLAY:" in output and "NONE" not in output.split("DISPLAY:")[1].split("\n")[1]
                is_power_audio = "AUDIO:" in output and "NONE" not in output.split("AUDIO:")[1].split("\n")[1]
                if is_power_video or is_power_audio:
                    return "YES"
                players = ["vlc", "chrome", "msedge", "wmplayer", "mpv", "gst-launch", "magicinfo", "webos"]
                for player in players:
                    check_cmd = f"powershell -Command \"Get-Process {player} -ErrorAction SilentlyContinue | Where-Object {{ $_.CPU -gt 0.5 }} | Select-Object -ExpandProperty Id\""
                    try:
                        res = subprocess.check_output(check_cmd, shell=True, creationflags=cflags, timeout=1).decode().strip()
                        if res: return "YES"
                    except: continue
        except: pass
        return "NO"

    def get_current_media_title(self):
        system = platform.system()
        cflags = 0x08000000 if system == "Windows" else 0
        try:
            if system == "Darwin":
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
                ignored = ["", "none", "desktop", "finder", "dock", "loginwindow", "system events", "control center", "siri", "notification center", "screen sharing"]
                if title.lower() in ignored: return "NONE"
                return title if len(title) > 2 else "NONE"
            elif system == "Windows":
                system_titles = ["task manager", "settings", "microsoft text input", "program manager", "calculator", "microsoft store", "windows input experience", "taskbar", "start"]
                fg_cmd = 'powershell -Command "$sig = \'[DllImport(\\\"user32.dll\\\", CharSet=CharSet.Auto)] public static extern int GetWindowText(IntPtr hWnd, System.Text.StringBuilder lpString, int nMaxCount); [DllImport(\\\"user32.dll\\\")] public static extern IntPtr GetForegroundWindow();\'; Add-Type -MemberDefinition $sig -Name Win32Utils -Namespace Win32; $hwnd = [Win32.Win32Utils]::GetForegroundWindow(); $sb = New-Object System.Text.StringBuilder 256; $null = [Win32.Win32Utils]::GetWindowText($hwnd, $sb, $sb.Capacity); $sb.ToString()"'
                try:
                    title = subprocess.check_output(fg_cmd, shell=True, creationflags=cflags, timeout=2).decode().strip()
                    if title and len(title) > 3 and title.lower() not in system_titles:
                        if any(ext in title.lower() for ext in [".mp4", ".mov", ".avi", ".mkv", ".jpg", ".png", ".webp", ".html"]):
                            return title
                        if any(p in title.lower() for p in ["magicinfo", "webos", "signage", "brightsign", "player"]):
                            return title
                except: pass
                smtc_cmd = 'powershell -Command "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null; $m = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync().GetAwaiter().GetResult(); $s = $m.GetCurrentSession(); if ($s) { $p = $s.TryGetMediaPropertiesAsync().GetAwaiter().GetResult(); $p.Title }"'
                try:
                    smtc_title = subprocess.check_output(smtc_cmd, shell=True, creationflags=cflags, timeout=2).decode().strip()
                    if smtc_title and len(smtc_title) > 2:
                        return smtc_title
                except: pass
                cmd = "powershell -Command \"Get-Process | Where-Object { $_.MainWindowTitle } | Select-Object -ExpandProperty MainWindowTitle\""
                try:
                    output = subprocess.check_output(cmd, shell=True, creationflags=cflags, timeout=3).decode().strip()
                    if output:
                        titles = output.split('\n')
                        media_exts = [".mp4", ".mov", ".avi", ".mkv", ".jpg", ".jpeg", ".png", ".webp", ".html", "index"]
                        for t in titles:
                            t = t.strip()
                            if any(ext in t.lower() for ext in media_exts):
                                return t
                        players_keywords = ["mpv", "vlc", "chrome", "chromium", "gst-launch", "magicinfo", "webos", "brightsign"]
                        for t in titles:
                            t = t.strip()
                            if any(k in t.lower() for k in players_keywords) and len(t) > 3:
                                return t
                except: pass
        except: pass
        return "NONE"

    def monitoring_loop(self):
        while self.is_running:
            try:
                manual_ip = (self.manual_ip_var.get() if self.is_gui else self.manual_ip_raw).strip()
                if manual_ip:
                    found_ip = manual_ip
                else:
                    found_ip = self.find_tracker_ip()
                active_id = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
                if found_ip:
                    if self.tracker_ip != found_ip:
                        self.log(f"🔄 Tracker IP for {active_id} changed or rediscovered: {found_ip}")
                        self.tracker_ip = found_ip
                    self.search_start_time = time.time()
                elapsed = time.time() - self.search_start_time
                if elapsed > 30:
                    self.log(f"⚠️ Tracker not found for {active_id}. Resetting...")
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
                media_title = media_title.replace(":", "-").replace("\n", " ").strip()
                if active_ports == "Internal/None": transmitting = "NO"
                if self.is_gui:
                    self.root.after(0, lambda: self.port_var.set(f"Active Port: {active_ports}"))
                    self.root.after(0, lambda: self.stream_var.set(f"Media Stream: {transmitting}"))
                source_time = int(time.time() * 1000)
                message = f"PULSE:{status}:{platform.node()}:{active_ports}:{source_time}:{transmitting}:{media_title}:{active_id}"
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
            if platform.system() == "Windows":
                s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            else:
                s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
            s.bind(('', BROADCAST_PORT))
            s.settimeout(1.5)
            try:
                data, addr = s.recvfrom(2048)
                parts = data.decode().split(":")
                if parts[0] == "DOOH_TRACKER_ADVERT":
                    raw_sites = parts[2].split(",")
                    target = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
                    my_node = platform.node()
                    for item in raw_sites:
                        site_parts = item.split("|")
                        if len(site_parts) < 2: continue
                        sid = site_parts[0]
                        owner = site_parts[1]
                        if sid.lower() == target.lower():
                            if owner == "NONE" or owner == my_node:
                                return addr[0]
                            else:
                                self.update_status(f"Site ID {sid} is in use by {owner}")
                                return None
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

def main():
    global USE_CUSTOM_TK
    parser = argparse.ArgumentParser(description="DOOH Source Monitor")
    parser.add_argument("--id", help="Target Tracker Site ID")
    parser.add_argument("--ip", help="Manual Tracker IP address")
    parser.add_argument("--cli", action="store_true", help="Run in CLI mode")
    args = parser.parse_args()

    # Orchestrator Logic: Spawn all previously active instances with their saved IPs
    if not args.id and not args.cli:
        identities_path = os.path.join(get_app_data_path(), "active_identities.json")
        if os.path.exists(identities_path):
            try:
                with open(identities_path, "r") as f:
                    data = json.load(f)
                    # Handle migration from old list structure
                    if "active_ids" in data and "identities" not in data:
                        identities = {sid: {"manual_ip": ""} for sid in data["active_ids"]}
                    else:
                        identities = data.get("identities", {})

                if identities:
                    # In a frozen build, sys.executable is DoohSourceMonitor.exe.
                    # Passing __file__ here used to make each child fail argparse,
                    # after which this parent exited and appeared not to launch.
                    for sid, cfg in identities.items():
                        manual_ip = cfg.get("manual_ip", "")
                        subprocess.Popen(
                            child_monitor_command(sid, manual_ip),
                            creationflags=child_creation_flags(),
                            env=child_environment(),
                        )
                    sys.exit(0)
            except Exception as e:
                write_startup_error("Auto-resume orchestrator failed", sys.exc_info())

    if not args.cli:
        root = None
        # Layer 1: Attempt Modern UI
        if USE_CUSTOM_TK:
            try:
                root = ctk.CTk()
            except Exception as e:
                write_startup_error("CustomTkinter window creation failed", sys.exc_info())
                USE_CUSTOM_TK = False

        # Layer 2: Attempt Standard UI
        if not root:
            try:
                import tkinter as tk
                from tkinter import messagebox, scrolledtext
                root = tk.Tk()
            except Exception as e:
                write_startup_error("Standard Tkinter window creation failed", sys.exc_info())
                return 1

        if root:
            try:
                if platform.system() == "Darwin":
                    try:
                        # Bring window to front on macOS
                        subprocess.Popen(['osascript', '-e', f'tell application "System Events" to set frontmost of the first process whose unix id is {os.getpid()} to true'])
                    except: pass

                app = SourceMonitorApp(root, initial_id=args.id, initial_ip=args.ip)
                root.mainloop()
            except Exception as e:
                write_startup_error("GUI runtime failed", sys.exc_info())
                return 1

    if args.cli:
        print("[*] Running in CLI mode...")
        app = SourceMonitorApp(None, initial_id=args.id, initial_ip=args.ip)
        try:
            while True: time.sleep(1)
        except KeyboardInterrupt:
            print("\n[*] Exiting monitor.")
            return 0

    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:
        write_startup_error("Unhandled startup exception", sys.exc_info())
        raise
import socket
import time
import subprocess
import os
import platform
import threading
import json
import argparse
import sys
import ipaddress
import traceback

# In a PyInstaller bundle, sys.executable is the monitor EXE itself.  Do not
# append __file__ when starting a child monitor; it points into the temporary
# one-file extraction directory and becomes an unwanted argparse argument.
IS_FROZEN = getattr(sys, "frozen", False)

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
IDENTITIES_FILE = os.path.join(DATA_DIR, "active_identities.json")
STARTUP_CRASH_LOG = os.path.join(DATA_DIR, "startup-crash.log")

def write_startup_error(context, exc_info=None):
    """Persist startup failures because --windowed deliberately has no console."""
    try:
        details = "".join(traceback.format_exception(*exc_info)) if exc_info else ""
        with open(STARTUP_CRASH_LOG, "a", encoding="utf-8") as f:
            f.write(
                "\n" + "=" * 72 + "\n"
                + time.strftime("%Y-%m-%d %H:%M:%S")
                + f" | {context}\n{details}"
            )
    except Exception:
        pass

def child_monitor_command(site_id, manual_ip=""):
    """Return a source-compatible or PyInstaller-compatible child command."""
    args = ["--id", site_id]
    if manual_ip:
        args.extend(["--ip", manual_ip])

    if IS_FROZEN:
        return [sys.executable, *args]
    return [sys.executable, os.path.abspath(__file__), *args]

def child_creation_flags():
    # A child has its own GUI window; it must not create a black console window.
    return subprocess.CREATE_NO_WINDOW if platform.system() == "Windows" else 0

def child_environment():
    """Make an auto-resumed frozen monitor a new, independent one-file app."""
    env = os.environ.copy()
    if IS_FROZEN:
        # PyInstaller 6.9+: without this, the child reuses the parent's _MEI
        # directory even though the parent exits immediately after spawning it.
        env["PYINSTALLER_RESET_ENVIRONMENT"] = "1"
    return env

if USE_CUSTOM_TK:
    try:
        ctk.set_appearance_mode("System")
        ctk.set_default_color_theme("blue")
    except:
        print("[*] Theme initialization failed. Using high-compatibility mode.")

class SourceMonitorApp:
    def __init__(self, root, initial_id=None, initial_ip=None):
        self.root = root
        self.is_gui = root is not None
        self.is_running = False
        self.tracker_ip = None
        self.search_start_time = 0
        self.is_hibernating = False

        if self.is_gui:
            title = "DOOH Source Monitor"
            if initial_id: title += f" - {initial_id}"
            self.root.title(title)
            self.root.geometry("540x680")

            if USE_CUSTOM_TK:
                self.target_id_var = ctk.StringVar(value="#Retin-")
                self.manual_ip_var = ctk.StringVar()
                self.status_msg_var = ctk.StringVar(value="Status: Stopped")
                self.host_var = ctk.StringVar(value=f"Source Host: {platform.node()}")
                self.port_var = ctk.StringVar(value="Active Port: Detecting...")
                self.stream_var = ctk.StringVar(value="Media Stream: No")
            else:
                self.target_id_var = tk.StringVar(value="#Retin-")
                self.manual_ip_var = tk.StringVar()
                self.status_msg_var = tk.StringVar(value="Status: Stopped")
                self.host_var = tk.StringVar(value=f"Source Host: {platform.node()}")
                self.port_var = tk.StringVar(value="Active Port: Detecting...")
                self.stream_var = tk.StringVar(value="Media Stream: No")

            # Enforce prefix and character limit
            self.target_id_var.trace_add("write", self._on_id_change)

            try:
                self.root.lift()
                self.root.focus_force()
            except:
                pass

        self.target_id_raw = initial_id or ""
        self.manual_ip_raw = initial_ip or ""

        if self.is_gui:
            self.load_config()
            # Prioritize CLI arguments over saved config
            if initial_id:
                if not initial_id.startswith("#Retin-"):
                    initial_id = f"#Retin-{initial_id}"
                self.target_id_var.set(initial_id)
                self.manual_ip_var.set(initial_ip if initial_ip else "")
            elif initial_ip:
                self.manual_ip_var.set(initial_ip)

            self.create_widgets()

        self.log("App initialized. Source platform: " + ("macOS" if platform.system() == "Darwin" else platform.system()))

        # Auto-resume: Start monitoring if an ID is present
        active_id = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
        if active_id and active_id != "#Retin-":
            self.log(f"Auto-starting with ID: {active_id}")
            if self.is_gui:
                self.root.after(500, self.toggle_monitoring)
            else:
                self.toggle_monitoring()

    def _on_id_change(self, *args):
        val = self.target_id_var.get()
        prefix = "#Retin-"
        if not val.startswith(prefix):
            self.target_id_var.set(prefix)
            return

        suffix = val[len(prefix):]
        if len(suffix) > 4:
            self.target_id_var.set(prefix + suffix[:4])

    def create_widgets(self):
        if USE_CUSTOM_TK:
            self.main_frame = ctk.CTkFrame(self.root, corner_radius=20)
            self.main_frame.pack(pady=25, padx=25, fill="both", expand=True)

            ctk.CTkLabel(self.main_frame, text="Target Tracker ID (Site ID)", font=("Helvetica", 18, "bold")).pack(pady=(25, 5))

            self.entry_id = ctk.CTkEntry(
                self.main_frame,
                textvariable=self.target_id_var,
                height=45,
                placeholder_text="e.g. 1234",
                corner_radius=22,
                justify='center',
                border_width=1,
                font=("Courier", 12)
            )
            self.entry_id.pack(pady=10, padx=30, fill="x")

            ctk.CTkLabel(self.main_frame, text="Manual Tracker IP (Optional)", font=("Helvetica", 14, "bold")).pack(pady=(10, 5))
            self.entry_ip = ctk.CTkEntry(
                self.main_frame,
                textvariable=self.manual_ip_var,
                height=35,
                placeholder_text="e.g. 192.168.1.100",
                corner_radius=17,
                justify='center',
                border_width=1,
                font=("Courier", 12)
            )
            self.entry_ip.pack(pady=5, padx=60, fill="x")

            ctk.CTkLabel(self.main_frame, text="(Leave blank for auto-discovery)", text_color="gray", font=("Helvetica", 11)).pack()

            # Telemetry Info
            ctk.CTkLabel(self.main_frame, textvariable=self.host_var, font=("Courier", 12)).pack(pady=(10, 0))
            ctk.CTkLabel(self.main_frame, textvariable=self.port_var, font=("Courier", 12)).pack()
            ctk.CTkLabel(self.main_frame, textvariable=self.stream_var, font=("Courier", 12)).pack()

            self.status_label = ctk.CTkLabel(self.main_frame, textvariable=self.status_msg_var, font=("Courier", 14, "italic"), text_color="#919A73")
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
            tk.Label(self.root, text="(Prefix #Retin- is constant)", font=('Helvetica', 8)).pack()

            tk.Label(self.root, text="Manual Tracker IP (Optional):").pack()
            self.entry_ip = tk.Entry(self.root, textvariable=self.manual_ip_var, width=30, justify='center')
            self.entry_ip.pack(pady=5)

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
                    if val and not val.startswith("#Retin-"):
                        val = f"#Retin-{val}"
                    self.target_id_var.set(val or "#Retin-")
                    self.manual_ip_var.set(data.get("manual_ip", ""))
            except: pass

    def save_config(self):
        current_id = self.target_id_var.get() if self.is_gui else self.target_id_raw
        current_ip = self.manual_ip_var.get() if self.is_gui else self.manual_ip_raw
        with open(CONFIG_FILE, "w") as f:
            json.dump({"target_id": current_id, "manual_ip": current_ip}, f)

    def _update_active_identities(self, site_id, active, manual_ip=""):
        try:
            site_id = site_id.strip()
            if not site_id: return
            identities = {}
            if os.path.exists(IDENTITIES_FILE):
                with open(IDENTITIES_FILE, "r") as f:
                    try:
                        data = json.load(f)
                        # Migration logic for old list-based active_ids
                        if "active_ids" in data and "identities" not in data:
                            for sid in data["active_ids"]:
                                identities[sid] = {"manual_ip": ""}
                        else:
                            identities = data.get("identities", {})
                    except: pass

            if active:
                identities[site_id] = {"manual_ip": manual_ip}
            else:
                if site_id in identities:
                    del identities[site_id]

            with open(IDENTITIES_FILE, "w") as f:
                json.dump({"identities": identities}, f)
        except: pass

    def toggle_monitoring(self):
        active_id = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
        manual_ip = (self.manual_ip_var.get() if self.is_gui else self.manual_ip_raw).strip()

        if not self.is_running:
            if not active_id or active_id == "#Retin-":
                if self.is_gui:
                    messagebox.showerror("Error", "Target Tracker ID is required.")
                    self.entry_id.focus_set()
                return

            if manual_ip:
                if not self._is_valid_local_network_ip(manual_ip):
                    if self.is_gui:
                        messagebox.showerror("Network Error", f"The IP {manual_ip} is not on the same local network (WiFi) as this computer.\n\nManual overrides must be on the same connection for security.")
                    else:
                        self.log(f"CRITICAL: IP {manual_ip} is rejected (Not on same local network).")
                    return

            self.save_config()
            self._update_active_identities(active_id, True, manual_ip)
            self.tracker_ip = None
            self.search_start_time = time.time()
            self.is_running = True
            self.is_hibernating = False
            if self.is_gui:
                if USE_CUSTOM_TK:
                    self.btn_toggle.configure(text="Searching (30s)", fg_color="#FFBF00", hover_color="#E5AB00", text_color="black")
                    self.entry_id.configure(state='disabled')
                    self.entry_ip.configure(state='disabled')
                else:
                    self.btn_toggle.config(text="Searching (30s)", bg="#FFBF00")
                    self.entry_id.config(state='disabled')
                    self.entry_ip.config(state='disabled')
                self.update_status("Status: Searching for Tracker...")
                self.root.after(100, self._update_smooth_timer)

            self.monitor_thread = threading.Thread(target=self.monitoring_loop, daemon=True)
            self.monitor_thread.start()
            self.log(f"Monitoring started for ID: {active_id}")
        else:
            self.is_running = False
            self.is_hibernating = False
            self._update_active_identities(active_id, False)
            if self.is_gui:
                if USE_CUSTOM_TK:
                    self.btn_toggle.configure(text="Start Monitoring", fg_color="#218838", hover_color="#297339", text_color="white")
                    self.entry_id.configure(state='normal')
                    self.entry_ip.configure(state='normal')
                else:
                    self.btn_toggle.config(text="Start Monitoring", bg="green")
                    self.entry_id.config(state='normal')
                    self.entry_ip.config(state='normal')
                self.update_status("Status: Stopped")
            self.log("Monitoring stopped.")

    def _is_valid_local_network_ip(self, ip):
        """Strict validation to ensure the IP is on the SAME local network as this machine."""
        try:
            # 1. Get host's own local IP
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            try:
                s.connect(('10.255.255.255', 1))
                local_ip = s.getsockname()[0]
            except:
                local_ip = '127.0.0.1'
            finally:
                s.close()

            if local_ip == '127.0.0.1' and ip != '127.0.0.1':
                return False

                # 2. Compare Subnets (First 3 octets /24)
            local_prefix = ".".join(local_ip.split('.')[:3])
            target_prefix = ".".join(ip.split('.')[:3])

            if local_prefix != target_prefix:
                self.log(f"Rejected IP {ip}: Not on same subnet as machine ({local_ip})")
                return False

            return True
        except:
            return False

    def _update_smooth_timer(self):
        if not self.is_running or self.tracker_ip:
            return
        elapsed = time.time() - self.search_start_time
        remaining = int(max(0, 30 - elapsed))
        status = self.status_msg_var.get()
        if "Searching" in status:
            text = f"Resetting in ({remaining}s)"
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
        system = platform.system()
        cflags = 0x08000000 if system == "Windows" else 0
        ports = []
        try:
            if system == "Darwin":
                ioreg_out = ""
                try: ioreg_out = subprocess.check_output(["ioreg", "-n", "AppleDisplay", "-r"], timeout=2).decode().upper()
                except: pass
                sp_out = ""
                try: sp_out = subprocess.check_output(["system_profiler", "SPDisplaysDataType"], timeout=3).decode().upper()
                except: pass
                if "HDMI" in ioreg_out or "HDMI" in sp_out: ports.append("HDMI")
                if "DISPLAYPORT" in ioreg_out or "DISPLAYPORT" in sp_out: ports.append("DP/USB-C")
                if "DVI" in ioreg_out or "DVI" in sp_out: ports.append("DVI")
                if "VGA" in ioreg_out or "VGA" in sp_out: ports.append("VGA")
                if not ports and sp_out.count("ONLINE: YES") > 1:
                    ports.append("External Monitor")
            elif system == "Windows":
                cmd = "powershell -Command \"Get-WmiObject Win32_PnPEntity | Where-Object { $_.Service -eq 'monitor' } | Select-Object -ExpandProperty Name\""
                output = subprocess.check_output(cmd, shell=True, creationflags=cflags, timeout=3).decode().strip()
                if output:
                    for line in output.split('\n'):
                        if "Generic" not in line or "PnP" in line:
                            ports.append(line[:15].strip())
                if not ports:
                    ports.append("Display Active")
            else:
                output = subprocess.check_output("xrandr --query", shell=True).decode()
                for line in output.split('\n'):
                    if " connected " in line and "primary" not in line:
                        ports.append(line.split()[0])
        except Exception as e:
            self.log(f"Port detection error: {e}")
        return ",".join(ports) if ports else "Internal/None"

    def check_transmission(self):
        system = platform.system()
        cflags = 0x08000000 if system == "Windows" else 0
        try:
            if system == "Darwin":
                output = subprocess.check_output(["pmset", "-g", "assertions"], timeout=2).decode()
                is_video = any("PreventUserIdleDisplaySleep" in l and " 1" in l for l in output.splitlines())
                is_audio = any("PreventUserIdleSystemSleep" in l and " 1" in l for l in output.splitlines())
                return "YES" if (is_video or is_audio) else "NO"
            elif system == "Windows":
                output = subprocess.check_output("powercfg /requests", shell=True, creationflags=cflags, timeout=2).decode().upper()
                is_power_video = "DISPLAY:" in output and "NONE" not in output.split("DISPLAY:")[1].split("\n")[1]
                is_power_audio = "AUDIO:" in output and "NONE" not in output.split("AUDIO:")[1].split("\n")[1]
                if is_power_video or is_power_audio:
                    return "YES"
                players = ["vlc", "chrome", "msedge", "wmplayer", "mpv", "gst-launch", "magicinfo", "webos"]
                for player in players:
                    check_cmd = f"powershell -Command \"Get-Process {player} -ErrorAction SilentlyContinue | Where-Object {{ $_.CPU -gt 0.5 }} | Select-Object -ExpandProperty Id\""
                    try:
                        res = subprocess.check_output(check_cmd, shell=True, creationflags=cflags, timeout=1).decode().strip()
                        if res: return "YES"
                    except: continue
        except: pass
        return "NO"

    def get_current_media_title(self):
        system = platform.system()
        cflags = 0x08000000 if system == "Windows" else 0
        try:
            if system == "Darwin":
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
                ignored = ["", "none", "desktop", "finder", "dock", "loginwindow", "system events", "control center", "siri", "notification center", "screen sharing"]
                if title.lower() in ignored: return "NONE"
                return title if len(title) > 2 else "NONE"
            elif system == "Windows":
                system_titles = ["task manager", "settings", "microsoft text input", "program manager", "calculator", "microsoft store", "windows input experience", "taskbar", "start"]
                fg_cmd = 'powershell -Command "$sig = \'[DllImport(\\\"user32.dll\\\", CharSet=CharSet.Auto)] public static extern int GetWindowText(IntPtr hWnd, System.Text.StringBuilder lpString, int nMaxCount); [DllImport(\\\"user32.dll\\\")] public static extern IntPtr GetForegroundWindow();\'; Add-Type -MemberDefinition $sig -Name Win32Utils -Namespace Win32; $hwnd = [Win32.Win32Utils]::GetForegroundWindow(); $sb = New-Object System.Text.StringBuilder 256; $null = [Win32.Win32Utils]::GetWindowText($hwnd, $sb, $sb.Capacity); $sb.ToString()"'
                try:
                    title = subprocess.check_output(fg_cmd, shell=True, creationflags=cflags, timeout=2).decode().strip()
                    if title and len(title) > 3 and title.lower() not in system_titles:
                        if any(ext in title.lower() for ext in [".mp4", ".mov", ".avi", ".mkv", ".jpg", ".png", ".webp", ".html"]):
                            return title
                        if any(p in title.lower() for p in ["magicinfo", "webos", "signage", "brightsign", "player"]):
                            return title
                except: pass
                smtc_cmd = 'powershell -Command "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null; $m = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync().GetAwaiter().GetResult(); $s = $m.GetCurrentSession(); if ($s) { $p = $s.TryGetMediaPropertiesAsync().GetAwaiter().GetResult(); $p.Title }"'
                try:
                    smtc_title = subprocess.check_output(smtc_cmd, shell=True, creationflags=cflags, timeout=2).decode().strip()
                    if smtc_title and len(smtc_title) > 2:
                        return smtc_title
                except: pass
                cmd = "powershell -Command \"Get-Process | Where-Object { $_.MainWindowTitle } | Select-Object -ExpandProperty MainWindowTitle\""
                try:
                    output = subprocess.check_output(cmd, shell=True, creationflags=cflags, timeout=3).decode().strip()
                    if output:
                        titles = output.split('\n')
                        media_exts = [".mp4", ".mov", ".avi", ".mkv", ".jpg", ".jpeg", ".png", ".webp", ".html", "index"]
                        for t in titles:
                            t = t.strip()
                            if any(ext in t.lower() for ext in media_exts):
                                return t
                        players_keywords = ["mpv", "vlc", "chrome", "chromium", "gst-launch", "magicinfo", "webos", "brightsign"]
                        for t in titles:
                            t = t.strip()
                            if any(k in t.lower() for k in players_keywords) and len(t) > 3:
                                return t
                except: pass
        except: pass
        return "NONE"

    def monitoring_loop(self):
        while self.is_running:
            try:
                manual_ip = (self.manual_ip_var.get() if self.is_gui else self.manual_ip_raw).strip()
                if manual_ip:
                    found_ip = manual_ip
                else:
                    found_ip = self.find_tracker_ip()
                active_id = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
                if found_ip:
                    if self.tracker_ip != found_ip:
                        self.log(f"🔄 Tracker IP for {active_id} changed or rediscovered: {found_ip}")
                        self.tracker_ip = found_ip
                    self.search_start_time = time.time()
                elapsed = time.time() - self.search_start_time
                if elapsed > 30:
                    self.log(f"⚠️ Tracker not found for {active_id}. Resetting...")
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
                media_title = media_title.replace(":", "-").replace("\n", " ").strip()
                if active_ports == "Internal/None": transmitting = "NO"
                if self.is_gui:
                    self.root.after(0, lambda: self.port_var.set(f"Active Port: {active_ports}"))
                    self.root.after(0, lambda: self.stream_var.set(f"Media Stream: {transmitting}"))
                source_time = int(time.time() * 1000)
                message = f"PULSE:{status}:{platform.node()}:{active_ports}:{source_time}:{transmitting}:{media_title}:{active_id}"
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
            if platform.system() == "Windows":
                s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            else:
                s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
            s.bind(('', BROADCAST_PORT))
            s.settimeout(1.5)
            try:
                data, addr = s.recvfrom(2048)
                parts = data.decode().split(":")
                if parts[0] == "DOOH_TRACKER_ADVERT":
                    raw_sites = parts[2].split(",")
                    target = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
                    my_node = platform.node()
                    for item in raw_sites:
                        site_parts = item.split("|")
                        if len(site_parts) < 2: continue
                        sid = site_parts[0]
                        owner = site_parts[1]
                        if sid.lower() == target.lower():
                            if owner == "NONE" or owner == my_node:
                                return addr[0]
                            else:
                                self.update_status(f"Site ID {sid} is in use by {owner}")
                                return None
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

def main():
    global USE_CUSTOM_TK
    parser = argparse.ArgumentParser(description="DOOH Source Monitor")
    parser.add_argument("--id", help="Target Tracker Site ID")
    parser.add_argument("--ip", help="Manual Tracker IP address")
    parser.add_argument("--cli", action="store_true", help="Run in CLI mode")
    args = parser.parse_args()

    # Orchestrator Logic: Spawn all previously active instances with their saved IPs
    if not args.id and not args.cli:
        identities_path = os.path.join(get_app_data_path(), "active_identities.json")
        if os.path.exists(identities_path):
            try:
                with open(identities_path, "r") as f:
                    data = json.load(f)
                    # Handle migration from old list structure
                    if "active_ids" in data and "identities" not in data:
                        identities = {sid: {"manual_ip": ""} for sid in data["active_ids"]}
                    else:
                        identities = data.get("identities", {})

                if identities:
                    # In a frozen build, sys.executable is DoohSourceMonitor.exe.
                    # Passing __file__ here used to make each child fail argparse,
                    # after which this parent exited and appeared not to launch.
                    for sid, cfg in identities.items():
                        manual_ip = cfg.get("manual_ip", "")
                        subprocess.Popen(
                            child_monitor_command(sid, manual_ip),
                            creationflags=child_creation_flags(),
                            env=child_environment(),
                        )
                    sys.exit(0)
            except Exception as e:
                write_startup_error("Auto-resume orchestrator failed", sys.exc_info())

    if not args.cli:
        root = None
        # Layer 1: Attempt Modern UI
        if USE_CUSTOM_TK:
            try:
                root = ctk.CTk()
            except Exception as e:
                write_startup_error("CustomTkinter window creation failed", sys.exc_info())
                USE_CUSTOM_TK = False

        # Layer 2: Attempt Standard UI
        if not root:
            try:
                import tkinter as tk
                from tkinter import messagebox, scrolledtext
                root = tk.Tk()
            except Exception as e:
                write_startup_error("Standard Tkinter window creation failed", sys.exc_info())
                return 1

        if root:
            try:
                if platform.system() == "Darwin":
                    try:
                        # Bring window to front on macOS
                        subprocess.Popen(['osascript', '-e', f'tell application "System Events" to set frontmost of the first process whose unix id is {os.getpid()} to true'])
                    except: pass

                app = SourceMonitorApp(root, initial_id=args.id, initial_ip=args.ip)
                root.mainloop()
            except Exception as e:
                write_startup_error("GUI runtime failed", sys.exc_info())
                return 1

    if args.cli:
        print("[*] Running in CLI mode...")
        app = SourceMonitorApp(None, initial_id=args.id, initial_ip=args.ip)
        try:
            while True: time.sleep(1)
        except KeyboardInterrupt:
            print("\n[*] Exiting monitor.")
            return 0

    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:
        write_startup_error("Unhandled startup exception", sys.exc_info())
        raise
