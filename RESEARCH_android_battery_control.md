# Android Battery Control for 24/7 Server Use: Comprehensive Research

## Table of Contents
1. [Charging Limit WITHOUT Root](#1-charging-limit-without-root)
2. [Charging Limit WITH Root (sysfs)](#2-charging-limit-with-root-sysfs)
3. [UserLand/PRoot Access to sysfs](#3-userland-proot-access-to-sysfs)
4. [Battery Bypass / Direct Power](#4-battery-bypass--direct-power)
5. [Thermal Management](#5-thermal-management)
6. [Existing Solutions](#6-existing-solutions)
7. [Practical Recommendations](#7-practical-recommendations)

---

## 1. Charging Limit WITHOUT Root

### Short Answer: No reliable programmatic control exists without root.

### What IS Available Without Root:

#### A. Built-in OEM Features (varies by manufacturer)
- **Samsung**: "Protect Battery" toggle (Settings > Battery) limits charging to 85%.
  Available on Galaxy S22+, Flip4+, Fold4+, A53, A73, M33, A23, A33.
- **Google Pixel**: "Adaptive Charging" / "Optimized Charging" slows charging and may
  limit to 80% during long overnight sessions. Not a hard cap.
- **OnePlus/OPPO**: "Optimized Charging" learns your routine and delays full charge.
- **ASUS**: Built-in charge limit option on ROG/Zenfone models.
- **Xiaomi**: MIUI has battery protection features on some models.

These are NOT programmable via any API. They are toggle-only in Settings.

#### B. Android BatteryManager API (Read-Only)
The `BatteryManager` API provides READ access to battery state but NO write/control:

```java
IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
Intent batteryStatus = context.registerReceiver(null, ifilter);

// Read battery percentage
int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
float batteryPct = level * 100 / (float) scale;

// Read temperature (in tenths of a degree Celsius)
int temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
float tempCelsius = temp / 10.0f;

// Read charging status
int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                   || status == BatteryManager.BATTERY_STATUS_FULL);

// Read health
int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
// BATTERY_HEALTH_GOOD, BATTERY_HEALTH_OVERHEAT, BATTERY_HEALTH_DEAD, etc.

// Read voltage (millivolts)
int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
```

**There is NO API to STOP or LIMIT charging.** Android's security model prevents
non-root apps from writing to kernel sysfs nodes.

#### C. Notification/Alarm-Based Workaround (No Root)
Apps like **AccuBattery** can:
- Monitor charge level
- Sound an alarm at a set percentage (e.g., 80%)
- YOU must manually unplug the charger

This is obviously useless for a 24/7 server scenario.

#### D. Hardware Solution: Chargie (~$35 USD)
- Physical USB dongle that sits between charger and phone
- Communicates via Bluetooth with a companion app
- Physically cuts power at your set charge level (e.g., 80%)
- Uses hysteresis: stops at 80%, resumes at 75% (configurable)
- Works autonomously once configured (no app connection needed)
- **This is the only reliable no-root solution for automated charge limiting**
- Website: https://chargie.org/

### Verdict for No-Root:
For a 24/7 server, your options are:
1. OEM built-in feature (if your phone has one) -- limited, not configurable
2. Chargie hardware dongle -- best no-root automated solution
3. Smart plug + automation -- crude but works (cut power at 80%, restore at 20%)

---

## 2. Charging Limit WITH Root (sysfs)

### How It Works
The Linux kernel exposes battery/charging hardware via sysfs virtual filesystem.
Root access lets you write to these files to enable/disable charging.

### Common sysfs Paths by Manufacturer

#### Generic / Most Common
```
/sys/class/power_supply/battery/charging_enabled        (1=on, 0=off)
/sys/class/power_supply/battery/battery_charging_enabled (1=on, 0=off)
/sys/class/power_supply/battery/charge_control_limit     (value in microamps)
/sys/class/power_supply/battery/input_suspend            (1=suspend, 0=normal)
```

#### Samsung
```
/sys/class/power_supply/battery/siop_level        (0-100, controls charge current %)
/sys/class/power_supply/battery/charge_type       (charge type control)
/sys/class/power_supply/battery/batt_slate_mode   (1=disable charging, 0=enable)
/sys/class/power_supply/battery/store_mode        (store mode for display units)
/sys/devices/platform/battery/power_supply/battery/siop_level
```

#### OnePlus / OPPO / Realme (Oplus)
```
/sys/class/power_supply/battery/charging_enabled          (OOS 11, 1/0)
/sys/class/power_supply/battery/mmi_charging_enable       (OOS 12+, 1/0)
/sys/class/oplus_chg/battery/mmi_charging_enabled         (OnePlus 10 Pro+, 1/0)
```

#### Google Pixel
```
/sys/devices/platform/google,charger/charge_stop_level    (set to e.g. 80)
/sys/devices/platform/google,charger/charge_start_level   (set to e.g. 70)
/sys/devices/platform/google,charger/bd_drainto            (battery defender)
```
Pixel devices uniquely support setting exact stop/start percentages.

#### Xiaomi / POCO
```
/sys/class/power_supply/battery/charging_enabled
/sys/class/power_supply/battery/battery_charging_enabled
/sys/class/power_supply/battery/input_suspend              (1=suspend)
```
**WARNING**: Some Xiaomi devices (notably Poco X3 Pro) have a faulty PMIC that
can be permanently damaged by toggling charging control. Research your specific
model before using.

#### Motorola
```
/sys/class/power_supply/battery/battery_charging_enabled   (1/0)
/sys/class/power_supply/battery/charging_enabled           (1/0)
```

#### LG
```
/sys/class/power_supply/battery/charging_enabled           (1/0)
/sys/class/power_supply/battery/store_demo_enabled         (store mode)
```

#### Qualcomm Chipset (generic paths)
```
/sys/class/power_supply/battery/charging_enabled
/sys/class/power_supply/battery/input_suspend
/sys/class/power_supply/usb/current_max                    (max input current)
/sys/class/power_supply/usb/voltage_max                    (max input voltage)
```

#### MediaTek Chipset
```
/sys/class/power_supply/battery/charging_enabled
/sys/devices/platform/battery/FG_daemon_disable            (fuel gauge)
```

#### Current and Voltage Control (Advanced)
```
/sys/class/power_supply/battery/constant_charge_current_max  (microamps)
/sys/class/power_supply/battery/voltage_max                  (microvolts)
/sys/class/power_supply/battery/current_max                  (input current limit)
```

### Example: Simple Charge Limiter Script (Root Required)
```bash
#!/system/bin/sh
# Simple charge limiter - stops at 80%, resumes at 70%
# Requires root access

CTRL_FILE="/sys/class/power_supply/battery/charging_enabled"
MAX_LEVEL=80
MIN_LEVEL=70

# Verify the control file exists and is writable
if [ ! -w "$CTRL_FILE" ]; then
    echo "Control file not found or not writable: $CTRL_FILE"
    echo "Try alternative paths..."
    for f in \
        "/sys/class/power_supply/battery/battery_charging_enabled" \
        "/sys/class/power_supply/battery/input_suspend" \
        "/sys/class/power_supply/battery/mmi_charging_enable"; do
        if [ -w "$f" ]; then
            CTRL_FILE="$f"
            echo "Found: $CTRL_FILE"
            break
        fi
    done
fi

while true; do
    LEVEL=$(cat /sys/class/power_supply/battery/capacity)
    TEMP=$(cat /sys/class/power_supply/battery/temp)
    TEMP_C=$((TEMP / 10))

    echo "Battery: ${LEVEL}% | Temp: ${TEMP_C}°C"

    # Emergency: disable charging if temperature > 40°C
    if [ "$TEMP_C" -gt 40 ]; then
        echo 0 > "$CTRL_FILE"
        echo "THERMAL ALERT: Charging disabled (${TEMP_C}°C)"
    elif [ "$LEVEL" -ge "$MAX_LEVEL" ]; then
        echo 0 > "$CTRL_FILE"
        echo "Charging DISABLED at ${LEVEL}%"
    elif [ "$LEVEL" -le "$MIN_LEVEL" ]; then
        echo 1 > "$CTRL_FILE"
        echo "Charging ENABLED at ${LEVEL}%"
    fi

    sleep 60
done
```

### Discovery: Finding Your Device's Control File
```bash
# Run as root to discover available control files
find /sys/class/power_supply/ -name "*charg*" -o -name "*enable*" \
    -o -name "*suspend*" -o -name "*limit*" -o -name "*siop*" \
    -o -name "*mmi*" -o -name "*slate*" 2>/dev/null

# List all battery-related sysfs entries
ls -la /sys/class/power_supply/battery/

# Check what's readable/writable
for f in /sys/class/power_supply/battery/*; do
    if [ -r "$f" ] && [ -f "$f" ]; then
        echo "$f = $(cat $f 2>/dev/null)"
    fi
done
```

---

## 3. UserLand/PRoot Access to sysfs

### Short Answer: PRoot CANNOT control charging. It needs actual root.

### Why PRoot Cannot Access sysfs for Battery Control:

1. **PRoot is a userspace tool**: It uses `ptrace()` to intercept system calls and
   translate paths. It does NOT provide real root privileges.

2. **sysfs permissions**: `/sys/class/power_supply/battery/*` files are owned by
   `root:root` with permissions like `0644` (readable by all, writable by root only)
   or `0664` (writable by root group). PRoot cannot bypass Linux kernel permission
   checks.

3. **Read vs Write**:
   - **READ**: PRoot/Termux CAN read some battery sysfs files:
     ```bash
     # This may work in Termux (no proot needed):
     cat /sys/class/power_supply/battery/capacity    # battery percentage
     cat /sys/class/power_supply/battery/temp         # temperature
     cat /sys/class/power_supply/battery/status       # Charging/Discharging/Full
     ```
   - **WRITE**: PRoot/Termux CANNOT write to sysfs files:
     ```bash
     # This will ALWAYS fail without real root:
     echo 0 > /sys/class/power_supply/battery/charging_enabled
     # Result: Permission denied
     ```

4. **PRoot-distro limitations**: Even running Ubuntu/Debian via proot-distro in
   Termux, you are still bound by Android's app sandbox. The "root" inside proot
   is fake -- it's UID 0 only within the proot namespace, not in the actual kernel.

5. **SELinux**: Even if you somehow got past Unix permissions, Android's SELinux
   policy (enforcing mode) blocks unprivileged processes from accessing most
   sysfs nodes for writing.

### What PRoot/Termux CAN Do (Without Root):
- Read battery status, temperature, voltage, current
- Monitor battery health
- Trigger notifications/alarms
- Run scripts that alert you to dangerous conditions
- Use `termux-battery-status` API for JSON battery info

### What Requires ACTUAL Root:
- Writing to any sysfs charging control file
- Enabling/disabling charging
- Setting charge current/voltage limits
- Modifying thermal thresholds

### Termux Battery Monitoring (No Root):
```bash
# Install Termux:API app and package
pkg install termux-api

# Get battery status as JSON
termux-battery-status
# Output:
# {
#   "health": "GOOD",
#   "percentage": 75,
#   "plugged": "PLUGGED_AC",
#   "status": "CHARGING",
#   "temperature": 28.5,
#   "current": 450000
# }

# Use in a monitoring script:
while true; do
    TEMP=$(termux-battery-status | python -c "import sys,json; print(json.load(sys.stdin)['temperature'])")
    if (( $(echo "$TEMP > 40" | bc -l) )); then
        termux-notification -t "BATTERY HOT" -c "Temperature: ${TEMP}°C"
        # Cannot actually stop charging without root!
    fi
    sleep 60
done
```

---

## 4. Battery Bypass / Direct Power

### Option A: Software-Based Bypass Charging (No Hardware Mod)

Some modern phones support "bypass charging" where the phone runs directly from
USB power, routing current around the battery:

**Samsung** (Galaxy S22+, Flip4+, Fold4+):
- Open **Gaming Hub** app > tap More > **Game Booster** > enable **"Pause USB Power Delivery"**
- Requires a USB-PD charger (25W+) and at least 20% battery
- Phone runs on wall power; battery stays at current level

**ASUS ROG Phones**:
- Built-in bypass charging mode in settings

**Google Pixel** (limited):
- Adaptive charging slows but doesn't fully bypass

**Sony Xperia** (gaming models):
- "HS Power Control" for bypass during gaming

### Option B: Physical Battery Removal + Dummy Battery

#### Which Phones Support Running Without Battery:
- **Most older phones with removable batteries** (pre-2016 era): Many will boot
  and run without a battery when plugged into USB. Examples: older Samsung Galaxy
  S/Note series, LG G-series.
- **Modern phones (non-removable battery)**: Require hardware modification.

#### The Capacitor + Diode Method:
1. Open the phone and carefully remove the battery
2. **Keep the BMS (Battery Management System) circuit** -- the small PCB attached
   to the battery. The phone needs this to recognize a "battery" is present.
3. Connect a **MUR460 diode** between USB power and the BMS
4. Optionally add a **4700uF capacitor** to smooth power delivery
5. The diode prevents backfeed; the capacitor handles current spikes

**Components needed**:
- MUR460 diode (or similar fast-recovery diode)
- 4700uF 6.3V electrolytic capacitor (optional but recommended)
- 6.8K resistor (to simulate battery thermistor if needed)
- USB cable (sacrificed for wires)
- Soldering equipment

**Risks and Warnings**:
- Phone may not boot if it can't detect a valid battery voltage
- Some phones check battery thermistor (NTC) -- need a ~10K resistor to simulate
- Power spikes during boot can cause instability without capacitor
- Incorrect voltage (must be ~3.7-4.2V, NOT 5V USB directly) can fry the phone
- **Use a buck converter** to step down 5V USB to ~3.7V if connecting directly
- Warranty void, potential fire hazard if done incorrectly

#### The "Keep Battery But Limit Charge" Alternative:
Instead of removing the battery, some people:
1. Root the phone
2. Use ACC to set charge limit to 50-60%
3. Keep battery as a built-in UPS
4. This is SAFER than battery removal and provides power backup

### Option C: Official Android Batteryless Support (Custom ROM/Build)
Android (since version 9) officially supports batteryless devices at the framework
level. When no battery is detected:
- System reports `battery_present = false`
- Device is considered "always charging" (plugged in)
- Low-battery shutdowns are disabled
- Battery-dependent jobs still run normally

This requires either:
- A custom kernel with proper `power_supply` charger driver (online=true)
- A custom Health HAL implementation
- PostmarketOS or similar custom OS that handles batteryless operation

---

## 5. Thermal Management

### Android APIs for Temperature Monitoring (No Root Required)

#### A. BatteryManager (All Android versions)
```java
// Battery temperature
IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
Intent battery = context.registerReceiver(null, filter);
int tempTenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
float tempC = tempTenths / 10.0f;  // Convert to Celsius
```

#### B. PowerManager Thermal API (Android 10+)
```java
// Register thermal status listener
PowerManager pm = getSystemService(PowerManager.class);
pm.addThermalStatusListener(status -> {
    switch (status) {
        case PowerManager.THERMAL_STATUS_NONE:      // Normal
        case PowerManager.THERMAL_STATUS_LIGHT:     // Light throttling
        case PowerManager.THERMAL_STATUS_MODERATE:  // Moderate throttling
        case PowerManager.THERMAL_STATUS_SEVERE:    // Severe throttling
        case PowerManager.THERMAL_STATUS_CRITICAL:  // Critical - near shutdown
        case PowerManager.THERMAL_STATUS_EMERGENCY: // Emergency - imminent shutdown
        case PowerManager.THERMAL_STATUS_SHUTDOWN:  // Shutting down
    }
});

// Poll current thermal status
int thermalStatus = pm.getCurrentThermalStatus();

// Predict thermal headroom (seconds until throttling)
// Available Android 11+
float headroom = pm.getThermalHeadroom(10); // 10 seconds forecast
// headroom >= 1.0 means throttling is imminent
```

#### C. From ADB/Shell (No Root for Reading)
```bash
# Battery temperature
cat /sys/class/power_supply/battery/temp
# Returns value in tenths of degree C (e.g., 285 = 28.5°C)

# CPU thermal zones
cat /sys/class/thermal/thermal_zone*/temp
cat /sys/class/thermal/thermal_zone*/type

# Dumpsys battery info
dumpsys battery

# Dumpsys thermal info (Android 10+)
dumpsys thermalservice
```

### Can Apps Reduce CPU Frequency? (Thermal Response)
- **Without root**: NO direct CPU frequency control. Apps can only:
  - Reduce their own workload (fewer threads, lower resolution, etc.)
  - Use Android's `PowerManager.WakeLock` judiciously
  - React to thermal callbacks by reducing work
  - Request Battery Saver mode (user interaction required)

- **With root**: YES, via sysfs:
  ```bash
  # Set CPU governor to powersave
  echo powersave > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor

  # Set max frequency (example: limit to 1.2GHz)
  echo 1200000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq

  # Disable CPU cores (reduce heat)
  echo 0 > /sys/devices/system/cpu/cpu2/online
  echo 0 > /sys/devices/system/cpu/cpu3/online
  ```

### Physical Thermal Management Tips for 24/7 Server:
1. **Remove the phone case** -- cases trap heat
2. **Place on a metal surface** (aluminum plate) for passive heatsinking
3. **Use a small USB fan** pointed at the phone
4. **Avoid direct sunlight** and warm locations
5. **Consider a heatsink** attached with thermal pad to the phone's back
6. **Keep ambient temperature below 25°C** if possible
7. **Disable unnecessary radios**: Bluetooth, NFC, GPS (if not needed)
8. **Lower screen brightness to minimum** or keep screen off
9. **Disable animations** in Developer Options

---

## 6. Existing Solutions

### A. ACC - Advanced Charging Controller (Root Required)
- **GitHub**: https://github.com/VR-25/acc
- **Requires**: Root (Magisk, KernelSU, or SuperSU)
- **Install**: Magisk module, or standalone shell script
- **Frontend app**: AccA (available on F-Droid)
- **How it works**:
  - Discovers device-specific sysfs charging control files automatically
  - Cycles through a database of known switches
  - Tests switches to verify they actually stop charging
  - Implements failover: if a switch fails 3 times, tries next one
  - Supports "battery idle mode" (bypass charging) when hardware supports it
- **Key features**:
  - Charge limit (e.g., stop at 80%, resume at 70%)
  - Temperature-based control (pause if too hot)
  - Cooldown cycles (periodic pause during charging)
  - Current/voltage limiting
  - Scheduling
  - Works across most rooted Android devices
- **Best for**: 24/7 server use with rooted phone. Most mature solution.

### B. Battery Charge Limit (Root Required)
- **GitHub**: https://github.com/MuntashirAkon/BatteryChargeLimiter
- **Requires**: Root
- **How it works**: Writes 0/1 to sysfs charging_enabled file
- **Key features**:
  - Simple charge limit with upper/lower thresholds
  - Voltage threshold setting
  - Widget for quick enable/disable
  - Custom control file path (if auto-detect fails)
- **Simpler than ACC** but less feature-rich

### C. Charge Control [ROOT] (Play Store)
- **Requires**: Root
- **How it works**: Same sysfs mechanism as above
- **Simple on/off** charging control with percentage triggers

### D. AccuBattery (No Root)
- **Does NOT actually control charging**
- **How it works**: Monitors battery and sends notification alarm at set level
- **Requires manual unplugging** -- useless for 24/7 server use
- **Useful for**: Battery health monitoring, capacity estimation, charge rate tracking

### E. Chargie (Hardware, No Root)
- **Price**: ~$35 USD
- **How it works**: Physical USB switch controlled via Bluetooth app
- **Features**:
  - Autonomous operation after initial configuration
  - Hysteresis charging (e.g., stop at 80%, resume at 75%)
  - USB data passthrough
  - Hardware-level voltage detection
- **Best for**: No-root automated charge limiting
- **Website**: https://chargie.org/

### F. Smart Plug + Automation (No Root, DIY)
- Use a WiFi smart plug (e.g., TP-Link Kasa, Shelly) with the charger
- Automate via Home Assistant, IFTTT, or Tasker:
  - Read battery level via Android API
  - Turn off smart plug at 80%
  - Turn on smart plug at 20%
- **Crude but effective** for 24/7 use
- Requires a home automation setup

### Comparison Table:

| Solution              | Root? | Auto? | Cost  | Reliability | Best For         |
|-----------------------|-------|-------|-------|-------------|------------------|
| ACC (Magisk)          | Yes   | Yes   | Free  | High        | Rooted 24/7 use  |
| Battery Charge Limit  | Yes   | Yes   | Free  | Medium      | Simple root use  |
| Chargie               | No    | Yes   | ~$35  | High        | No-root auto     |
| AccuBattery           | No    | No*   | Free  | N/A         | Monitoring only  |
| Smart Plug            | No    | Yes   | ~$15  | Medium      | DIY automation   |
| OEM Built-in          | No    | Yes   | Free  | High        | Supported phones |
| Battery Removal       | No    | N/A   | ~$5   | Variable    | Permanent server |

*AccuBattery only sends notifications; does not stop charging.

---

## 7. Practical Recommendations

### Tier 1: Best Approach (Rooted Phone)
If you can root the phone, this is the safest and most reliable approach:

1. **Root via Magisk** (preserves OTA capability)
2. **Install ACC** (Advanced Charging Controller) as Magisk module
3. **Configure ACC**:
   ```bash
   # Set charge limit: stop at 70%, resume at 60%
   acc -s pause_capacity=70
   acc -s resume_capacity=60

   # Set temperature limits
   acc -s cooldown_temp=40
   acc -s max_temp=45
   acc -s shutdown_temp=55

   # Limit charging current (reduce heat)
   acc -s max_charging_current=500  # 500mA slow charge

   # Enable battery idle mode if supported
   acc -s prioritize_batt_idle_mode=true
   ```
4. **Monitor** via AccA app or `acc -i` command
5. **Set up thermal monitoring script** for additional safety

### Tier 2: No Root, Automated (Hardware Solution)
If you cannot root:

1. **Buy a Chargie** dongle (~$35)
2. Set charge limit to 70-80%
3. The device handles everything automatically
4. OR use a **WiFi smart plug** with Tasker automation

### Tier 3: Battery Removal (Permanent Server, Advanced)
For a dedicated permanent server:

1. Choose a phone with **PostmarketOS support** (OnePlus 6T recommended)
2. Flash PostmarketOS or another Linux distro
3. Physically remove the battery, keeping the BMS circuit
4. Wire USB power through a MUR460 diode to the BMS
5. Add a 4700uF capacitor for stability
6. Hot-glue everything securely

**Pros**: No battery degradation ever, no swelling risk
**Cons**: No UPS capability, requires hardware skills, risk of bricking

### Tier 4: Minimal Effort (Accept Some Risk)
If you cannot root and don't want hardware mods:

1. Enable **OEM battery protection** (Samsung 85%, etc.) if available
2. Install **AccuBattery** for monitoring
3. Keep the phone **cool** (remove case, use fan, metal surface)
4. **Disable unnecessary features** (Bluetooth, NFC, GPS, animations)
5. **Lower screen brightness** or keep screen off
6. Monitor temperature manually; if consistently above 35°C, improve cooling
7. **Replace the battery annually** ($10-30 for most phones)
8. **Inspect for swelling** monthly (phone wobbles on flat surface = swollen)

### General Safety Guidelines for All Approaches:

1. **Temperature is the #1 enemy**: Keep battery below 35°C during use.
   Above 40°C sustained = accelerated degradation. Above 60°C = danger.

2. **Charge level matters**: Li-ion batteries degrade fastest at 100% and 0%.
   Ideal range for longevity: 20-80%. For 24/7 plugged-in use, 50-70% is ideal.

3. **Slow charging is better**: Limiting charge current to 500mA generates less
   heat than fast charging at 2-3A.

4. **Monthly inspection**: Check for battery swelling by placing the phone on a
   flat surface. If it rocks/wobbles, the battery is swelling. STOP using it.

5. **Fire safety**: Keep a 24/7 phone on a non-flammable surface. Not on a bed,
   couch, or paper. A ceramic tile or metal plate is ideal.

6. **Ventilation**: Ensure airflow around the phone. Never stack items on top of it.

7. **UPS consideration**: If you keep the battery in, it acts as a built-in UPS.
   Setting charge to 50-60% gives both longevity and backup power.

---

## Sources and References

- [ACC - Advanced Charging Controller (GitHub)](https://github.com/VR-25/acc)
- [Battery Charge Limiter (GitHub)](https://github.com/MuntashirAkon/BatteryChargeLimiter)
- [Chargie - USB Charge Limiter](https://chargie.org/)
- [XDA Forums - Battery Charging Limit Control Files](https://xdaforums.com/t/battery-charging-limit-control-file-data-path-root.4503353/)
- [XDA Forums - Root Command to Limit Charge Level](https://xdaforums.com/t/root-command-to-limit-charge-level.4637823/)
- [Android Developers - BatteryManager API](https://developer.android.com/reference/android/os/BatteryManager)
- [Android Developers - Thermal API](https://developer.android.com/games/optimize/adpf/thermal)
- [Android Source - Thermal Mitigation](https://source.android.com/docs/core/power/thermal-mitigation)
- [Android Source - Batteryless Device Support](https://source.android.com/docs/core/power/batteryless)
- [CrackOverflow - Batteryless Home Server Guide](https://crackoverflow.com/docs/system_administration/containerization/turn_android_phone_to_batteryless_home_server/)
- [Samsung Bypass Charging](https://www.androidauthority.com/galaxy-s23-bypass-charging-feature-more-phones-3282489/)
- [Hackaday - Limiting Battery Risk on Repurposed Smartphones](https://hackaday.com/2026/02/01/limiting-battery-risk-on-repurposed-smartphones-with-postmarketos/)
- [XDA Forums - Battery Charge Limit App](https://xdaforums.com/t/app-root-4-0-battery-charge-limit-v1-1-1.3557002/)
- [Termux PRoot-Distro sysfs Issue](https://github.com/termux/proot-distro/issues/403)
- [AccuBattery - Charging Research](https://accubattery.zendesk.com/hc/en-us/articles/210224725-Charging-research-and-methodology)
