import socket
import time
import subprocess
import os
import platform
import threading
import json
import argparse
import sys

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
        self.is_hibernating = False # New state for auto-reconnect

        if self.is_gui:
            self.root.title("DOOH Source Monitor")
            self.root.geometry("540x500")

            # Variables
            if USE_CUSTOM_TK:
                self.target_id_var = ctk.StringVar()
                self.status_msg_var = ctk.StringVar(value="Status: Stopped")
            else:
                self.target_id_var = tk.StringVar()
                self.status_msg_var = tk.StringVar(value="Status: Stopped")

            # OS-specific window fixes
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

        if initial_id:
            self.log(f"Auto-starting with ID: {initial_id}")
            self.root.after(500, self.toggle_monitoring)

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
                corner_radius=22.5, # Highly rounded edges (height / 2)
                justify='center',
                border_width=1,
                font=("Courier", 12.5)
            )
            self.entry_id.pack(pady=10)
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
                fg_color="#297339",
                hover_color="#2A6337"
            )
            self.btn_toggle.pack(pady=20)
            self.btn_toggle.pack(pady=10, padx=30)

            ctk.CTkLabel(self.main_frame, text="Activity Log", font=("Helvetica", 12, "bold")).pack(anchor="w", padx=35, pady=(15, 0))
            self.log_area = ctk.CTkTextbox(self.main_frame, height=160, corner_radius=15, font=("Courier", 12))
            self.log_area.pack(pady=10, padx=30, fill="x")
            self.log_area.configure(state="disabled")
        else:
            # Target ID Input
            tk.Label(self.root, text="Target Tracker ID (Site ID):", font=('Helvetica', 10, 'bold')).pack(pady=(20, 5))
            self.entry_id = tk.Entry(self.root, textvariable=self.target_id_var, width=30, justify='center', font=('Helvetica', 12))
            self.entry_id.pack(pady=5)
            tk.Label(self.root, text="(Required to link a specific tracker)", fg="gray").pack()

            # Status Display
            tk.Label(self.root, textvariable=self.status_msg_var, font=('Helvetica', 10)).pack(pady=10)

            # Control Buttons
            self.btn_toggle = tk.Button(self.root, text="Start Monitoring", command=self.toggle_monitoring,
                                        bg="#4CAF50", fg="black", height=2, width=20)
            self.btn_toggle.pack(pady=10)

            # Log Area
            tk.Label(self.root, text="Activity Log:").pack(anchor="w", padx=20)
            self.log_area = scrolledtext.ScrolledText(self.root, height=10, width=55, state='disabled', font=('Courier', 9))
            self.log_area.pack(pady=5, padx=10)

    def log(self, message):
        timestamp = time.strftime("%H:%M:%S")
        full_msg = f"[{timestamp}] {message}"

        if self.is_gui:
            self.root.after(0, self._safe_log_update, full_msg)
        else:
            print(full_msg)

        # Write to file
        try:
            with open(LOG_FILE, "a") as f:
                f.write(full_msg + "\n")
        except:
            pass

    def _safe_log_update(self, msg):
        self.log_area.configure(state='normal')
        if USE_CUSTOM_TK:
            self.log_area.insert("end", msg + "\n")
            self.log_area.see("end")
        else:
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
            except:
                pass

    def save_config(self):
        current_id = self.target_id_var.get() if self.is_gui else self.target_id_raw
        with open(CONFIG_FILE, "w") as f:
            json.dump({"target_id": current_id}, f)

    def toggle_monitoring(self):
        if not self.is_running:
            # Validation: Ensure Site ID is not empty
            active_id = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()
            if not active_id:
                if self.is_gui:
                    messagebox.showerror("Error", "Target Tracker ID is required.")
                    self.entry_id.focus_set()
                else:
                    self.log("[!] Error: Target Tracker ID is required.")
                return

            if self.is_gui:
                self.target_id_var.set(active_id) # Update UI with stripped ID

            self.save_config()
            self.tracker_ip = None # Reset IP to force a fresh discovery and ID validation
            self.search_start_time = time.time()
            self.is_running = True
            self.is_hibernating = False # Stop any background watchdog
            if self.is_gui:
                if USE_CUSTOM_TK:
                    self.btn_toggle.configure(text="Searching (30s)", fg_color="#FFBF00", hover_color="#E5AB00", text_color="black")
                    self.entry_id.configure(state='disabled')
                else:
                    self.btn_toggle.config(text="Searching (30s)", bg="#FFBF00", fg="black")
                    self.entry_id.config(state='disabled')
                self.update_status("Status: Searching for Tracker...")
                self.root.after(100, self._update_smooth_timer)

            self.monitor_thread = threading.Thread(target=self.monitoring_loop, daemon=True)
            self.monitor_thread.start()

            self.log("Monitoring started for ID: " + active_id + " ✔")
        else:
            self.is_running = False
            self.is_hibernating = False # Manual stop clears auto-reconnect
            if self.is_gui:
                if USE_CUSTOM_TK:
                    self.btn_toggle.configure(text="Start Monitoring", fg_color="#28a745", hover_color="#218838")
                    self.entry_id.configure(state='normal')
                else:
                    self.btn_toggle.config(text="Start Monitoring", bg="#4CAF50")
                    self.entry_id.config(state='normal')
                self.update_status("Status: Stopped")
            self.log("Monitoring stopped.")

    def _update_smooth_timer(self):
        if not self.is_running or self.tracker_ip:
            return

        elapsed = time.time() - self.search_start_time
        remaining = int(max(0, 30 - elapsed))

        status = self.status_msg_var.get()
        if "Searching for Tracker" in status:
            text = f"Resetting in ({remaining}s)"
            if USE_CUSTOM_TK:
                self.btn_toggle.configure(text=text, fg_color="#FFBF00", hover_color="#E5AB00", text_color="black")
            else:
                self.btn_toggle.config(text=text, bg="#FFBF00", fg="black")
        else:
            # If not explicitly searching (e.g., Connection Lost), keep the "Stop Monitoring" appearance
            if USE_CUSTOM_TK:
                self.btn_toggle.configure(text="Stop Monitoring", fg_color="#dc3545", hover_color="#c82333", text_color="white")
            else:
                self.btn_toggle.config(text="Stop Monitoring", bg="#f44336", fg="white")

        if remaining > 0:
            self.root.after(100, self._update_smooth_timer)

    def is_media_playing(self):
        system = platform.system()
        if system == "Windows":
            players = ["vlc", "mpc-hc", "chrome", "wmplayer", "powershell"]
            cmd = 'tasklist'
        elif system == "Darwin":
            players = ["vlc", "quicktime player", "iina", "chrome", "safari", "plex"]
            cmd = 'ps -e'
        else:
            players = ["vlc", "mpv", "chrome", "firefox"]
            cmd = 'ps -e'

        try:
            output = subprocess.check_output(cmd, stderr=subprocess.STDOUT).decode().lower()
            return any(player in output for player in players)
        except:
            return True

    def get_active_ports(self):
        """Detects physical display output ports currently active."""
        system = platform.system()
        ports = []
        try:
            if system == "Darwin":
                # 1. Fast check via ioreg (Hardware Registry)
                ioreg_out = subprocess.check_output(["ioreg", "-n", "AppleDisplay", "-r"]).decode().upper()
                # 2. Detailed check via system_profiler (Connection Types)
                sp_out = subprocess.check_output(["system_profiler", "SPDisplaysDataType"]).decode()
                
                # Check for active external connection markers
                if "HDMI" in ioreg_out or "HDMI" in sp_out.upper(): ports.append("HDMI")
                if "DISPLAYPORT" in ioreg_out or "DISPLAYPORT" in sp_out.upper(): ports.append("DP/USB-C")
                if "DVI" in ioreg_out or "DVI" in sp_out.upper(): ports.append("DVI")
                if "VGA" in ioreg_out or "VGA" in sp_out.upper(): ports.append("VGA")
                
                # If we see "Online: Yes" for a non-internal display but can't identify type
                if not ports and sp_out.count("Online: Yes") > 1:
                    ports.append("External Monitor")

            elif system == "Windows":
                # Check for monitors with "Availability = 3" (Running/Full Power)
                cmd = "powershell \"Get-CimInstance Win32_DesktopMonitor | Where-Object {$_.Availability -eq 3} | Select-Object -ExpandProperty Caption\""
                output = subprocess.check_output(cmd, shell=True).decode().strip()
                if output:
                    ports.append(output.split('\n')[0][:15]) # Get first monitor name
                else:
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
        try:
            if system == "Darwin":
                # Precise check of macOS Power Assertions
                output = subprocess.check_output(["pmset", "-g", "assertions"]).decode()
                is_video = False
                is_audio = False
                for line in output.splitlines():
                    # Only return YES if the specific media assertions are set to 1
                    if "PreventUserIdleDisplaySleep" in line and " 1" in line:
                        is_video = True
                    if "PreventUserIdleSystemSleep" in line and " 1" in line:
                        is_audio = True
                return "YES" if (is_video or is_audio) else "NO"
            elif system == "Windows":
                output = subprocess.check_output("powercfg /requests", shell=True).decode()
                # Windows lists active drivers/processes under [VIDEO] or [AUDIO]
                # If these sections are not "None", transmission is happening
                is_video = "DISPLAY:" in output and "None" not in output.split("DISPLAY:")[1].splitlines()[0]
                is_audio = "AUDIO:" in output and "None" not in output.split("AUDIO:")[1].splitlines()[0]
                return "YES" if (is_video or is_audio) else "NO"
        except:
            pass
        return "NO"

    def monitoring_loop(self):
        while self.is_running:
            try:
                if not self.tracker_ip:
                    elapsed = time.time() - self.search_start_time
                    remaining = int(max(0, 30 - elapsed))

                    # Reset after 30 seconds of searching without a match
                    if elapsed > 30:
                        self.log(f"⚠️ Tracker not found for {self.target_id_var.get() if self.is_gui else self.target_id_raw}. Resetting...")
                        if self.is_gui:
                            self.root.after(0, self._start_hibernation)
                        else:
                            self.is_running = False
                            print("[*] Monitoring stopped due to search timeout.")
                        return

                    self.update_status("Status: Searching for Tracker...")

                    # Non-blocking search (short timeout)
                    self.tracker_ip = self.find_tracker_ip()
                    if not self.tracker_ip:
                        time.sleep(0.1) # Brief pause to prevent CPU pegging
                        continue

                    self.log(f"Connected to tracker at {self.tracker_ip}")

                # Check health and send heartbeat
                status = "HEALTHY" if self.is_media_playing() else "NO_MEDIA"
                active_ports = self.get_active_ports()
                transmitting = self.check_transmission()
                
                # Logic: Even if media is playing, if no external ports are active, 
                # we are not "transmitting" to a target screen.
                if active_ports == "Internal/None":
                    transmitting = "NO"

                source_time = int(time.time() * 1000) # Current system time in ms
                
                # Format: PULSE:{status}:{hostname}:{ports}:{timestamp}:{transmitting}
                message = f"PULSE:{status}:{platform.node()}:{active_ports}:{source_time}:{transmitting}"

                with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                    s.settimeout(2.0)
                    s.sendto(message.encode(), (self.tracker_ip, HEARTBEAT_PORT))
                    self.update_status(f"Status: Connected to {self.tracker_ip}")
                    # Ensure button is Red/Stop when connected
                    if self.is_gui:
                        self.root.after(0, lambda: self.btn_toggle.configure(text="Stop Monitoring", fg_color="#dc3545", hover_color="#c82333", text_color="white") if USE_CUSTOM_TK else self.btn_toggle.config(text="Stop Monitoring", bg="#f44336", fg="white"))
                    # Reset timeout once connection is established/maintained
                    self.search_start_time = time.time()
            except Exception as e:
                self.log(f"Connection lost: {e}")
                self.tracker_ip = None
                self.update_status("Status: Connection Lost. Retrying...")
                # Start 30s countdown upon connection loss
                self.search_start_time = time.time()
                if self.is_gui:
                    self.root.after(0, self._update_smooth_timer)

            time.sleep(BEAT_INTERVAL)

    def find_tracker_ip(self):
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.bind(('', BROADCAST_PORT))
            s.settimeout(0.5) # Short timeout for smooth UI countdown
            try:
                data, addr = s.recvfrom(1024)
                parts = data.decode().split(":")
                # Expected format from Android: DOOH_TRACKER_ADVERT:PORT:SITE_ID
                if parts[0] == "DOOH_TRACKER_ADVERT":
                    found_id = parts[2] if len(parts) > 2 else "UNKNOWN"
                    target = (self.target_id_var.get() if self.is_gui else self.target_id_raw).strip()

                    if target.lower() == found_id.lower():
                        self.log(f"✅ Site ID Match: {found_id} ({addr[0]})")
                        return addr[0]
                    else:
                        # Only log mismatch once every 5 seconds to avoid flooding
                        if not hasattr(self, '_last_mismatch_log') or time.time() - self._last_mismatch_log > 5:
                            if not self.is_hibernating: # Don't log mismatches during quiet hibernation
                                self.log(f"ℹ️ Found different tracker: {found_id} (Skipping)")
                            self._last_mismatch_log = time.time()
            except socket.timeout:
                pass
            except Exception as e:
                if not self.is_hibernating:
                    self.log(f"Discovery error: {e}")
            return None

    def _start_hibernation(self):
        """Called on timeout. Resets UI but starts passive background discovery."""
        if self.is_running:
            self.toggle_monitoring() # UI stop
        
        self.is_hibernating = True
        self.update_status("Status: Offline (Auto-resume active)")
        self.log("Waiting for network/tracker recovery... 💤")
        threading.Thread(target=self._hibernation_watchdog, daemon=True).start()

    def _hibernation_watchdog(self):
        """Passive background loop that waits for the tracker to reappear."""
        while self.is_hibernating and not self.is_running:
            ip = self.find_tracker_ip()
            if ip:
                self.log("✨ Network/Tracker recovered! Resuming monitoring...")
                self.is_hibernating = False
                if self.is_gui:
                    self.root.after(0, self.toggle_monitoring)
                return
            time.sleep(5) # Quiet check every 5 seconds

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="DOOH Source Monitor")
    parser.add_argument("--id", help="Target Tracker Site ID")
    parser.add_argument("--cli", action="store_true", help="Run in CLI mode (no GUI)")
    args = parser.parse_args()

    # Determine if we should attempt GUI
    use_gui = not args.cli

    if use_gui:
        try:
            if USE_CUSTOM_TK:
                root = ctk.CTk()
            else:
                root = tk.Tk()

            # macOS-specific activation
            if platform.system() == "Darwin":
                try:
                    # Try to bring the window to the very front using AppleScript
                    curr_pid = os.getpid()
                    script = f'tell application "System Events" to set frontmost of the first process whose unix id is {curr_pid} to true'
                    subprocess.Popen(['osascript', '-e', script])
                except:
                    # Fallback for older macOS
                    os.system('''/usr/bin/osascript -e 'tell app "Finder" to set frontmost of process "python" to true' ''')

            app = SourceMonitorApp(root, initial_id=args.id)

            # Final attempts to force visibility
            root.update_idletasks()
            root.lift()
            if not USE_CUSTOM_TK:
                root.attributes('-topmost', True)
                root.after(500, lambda: root.attributes('-topmost', False))
            root.focus_force()

            root.mainloop()
        except Exception as e:
            print(f"[*] GUI could not be initialized: {e}")
            print("[*] Falling back to CLI mode...")
            use_gui = False

    if not use_gui:
        # CLI Mode Execution
        app = SourceMonitorApp(None, initial_id=args.id)
        print(f"[*] CLI Monitor active for ID: {args.id or 'ANY'}")
        print("[*] Press Ctrl+C to stop.")
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            app.is_running = False
            print("\n[!] Exiting...")
            sys.exit(0)
