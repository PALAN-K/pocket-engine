# Networking Reference - PocketServer

## Table of Contents
1. [Local Network (MVP Scope)](#local-network-mvp-scope)
2. [Dropbear SSH Server](#dropbear-ssh-server)
3. [Tunneling Options (Future, NOT MVP)](#tunneling-options-future-not-mvp)
4. [Network Monitoring](#network-monitoring)

---

## Local Network (MVP Scope)

SSH access over local WiFi. Users connect from any device on the same network.

**Display format:** `ssh user@192.168.x.x -p 2022`

### Get Device WiFi IP Address (Kotlin)

```kotlin
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.net.Inet4Address

fun getWifiIpAddress(connectivityManager: ConnectivityManager): String? {
    val network = connectivityManager.activeNetwork ?: return null
    val linkProps: LinkProperties = connectivityManager.getLinkProperties(network) ?: return null
    return linkProps.linkAddresses
        .map { it.address }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
}
```

### IP Change Detection

Poll every 10 seconds or use `NetworkCallback` (see [Network Monitoring](#network-monitoring)) to detect IP changes and auto-update the dashboard display.

### Why Most AI Agents Need No Public URL

Outbound connections work behind any NAT/firewall with no tunneling:

| Agent Platform | Protocol | Direction |
|----------------|----------|-----------|
| Telegram | Long-polling (`getUpdates`) | Bot polls Telegram servers |
| WhatsApp | Baileys (WebSocket) | Outbound to WhatsApp servers |
| Discord | discord.js (WebSocket) | Outbound to Discord gateway |
| Slack | Socket Mode (WebSocket) | Outbound to Slack servers |

Public URLs are only needed for inbound webhook mode, which is NOT required for MVP.

---

## Dropbear SSH Server

Lightweight SSH server (~110KB binary). Already bundled in UserLand.

| Setting | Value |
|---------|-------|
| Default port | 2022 (Android restricts ports < 1024) |
| Binary location | Provided by UserLand filesystem |
| Host key | Auto-generated on first run (`-R` flag) |
| Auth | Password (default) |

### Launch Command

```bash
dropbear -E -p 2022 -R -F
```

- `-E` -- log to stderr (captures output for the app)
- `-p 2022` -- listen on port 2022
- `-R` -- generate host keys if missing
- `-F` -- run in foreground (required for process management)

### Security Configuration

```bash
# Disable root login: add to dropbear launch
dropbear -E -p 2022 -R -F -w

# -w flag disables root login
```

**Auto-generated password (Kotlin):**

```kotlin
fun generatePassword(length: Int = 12): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length).map { chars.random(java.security.SecureRandom().asKotlinRandom()) }.joinToString("")
}
```

**SSH key auth guide for users:**
1. User runs `ssh-keygen` on their client machine
2. Copy public key: `ssh-copy-id -p 2022 user@<device-ip>`
3. Disable password auth once keys are set: add `-s` flag to dropbear launch

---

## Tunneling Options (Future, NOT MVP)

Premium features for remote access. All require ARM64 Linux binaries running inside UserLand.

### Cloudflare Quick Tunnel (No account needed)

```bash
cloudflared tunnel --url http://localhost:2022
```

- Generates random `*.trycloudflare.com` URL
- URL changes on every restart (not persistent)
- Binary: `cloudflared-linux-arm64`
- Zero config, no account, no domain

### ngrok (Free static domain)

```bash
ngrok tcp 2022 --domain your-name.ngrok-free.dev
```

- 1 free static domain per account (`*.ngrok-free.dev`)
- ARM64 binary available from ngrok downloads
- Free tier: 1 GB/month bandwidth
- Persistent URL across restarts

### Cloudflare Named Tunnel (Stable, needs domain)

- Requires Cloudflare account + registered domain (~$5/year)
- Persistent URL with unlimited bandwidth
- Best long-term production solution
- Setup: `cloudflared tunnel create pocketserver` then configure DNS

---

## Network Monitoring

Real-time connectivity tracking using `ConnectivityManager.NetworkCallback`.

### Monitor Network Changes (Kotlin)

```kotlin
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

fun registerNetworkMonitor(
    connectivityManager: ConnectivityManager,
    onAvailable: (String?) -> Unit,
    onLost: () -> Unit
) {
    val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val ip = getWifiIpAddress(connectivityManager)
            onAvailable(ip) // Update dashboard with new IP
        }

        override fun onLost(network: Network) {
            onLost() // Show "WiFi disconnected" warning on dashboard
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val ip = getWifiIpAddress(connectivityManager)
            onAvailable(ip) // IP may have changed
        }
    })
}
```

### Dashboard States

| State | Display |
|-------|---------|
| WiFi connected | `ssh user@192.168.x.x -p 2022` (green) |
| WiFi disconnected | "No WiFi connection" warning (red) |
| IP changed | Auto-update displayed IP, flash highlight |
