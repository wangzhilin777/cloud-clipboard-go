# Cloud Clipboard Go

<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/lang-简体中文-blue.svg" alt="中文 Readme"></a>
  <a href="https://raw.githubusercontent.com/jonnyan404/cloud-clipboard-go-launcher/main/LICENSE">
    <img src="https://img.shields.io/github/license/jonnyan404/cloud-clipboard-go-launcher?color=brightgreen" alt="license">
  </a>
  <a href="https://github.com/jonnyan404/cloud-clipboard-go/releases/latest">
    <img src="https://img.shields.io/github/v/release/jonnyan404/cloud-clipboard-go?color=brightgreen&include_prereleases" alt="release">
  </a>
  <a href="https://github.com/jonnyan404/cloud-clipboard-go/releases/latest">
    <img src="https://img.shields.io/github/downloads/jonnyan404/cloud-clipboard-go/total?color=brightgreen&include_prereleases" alt="downloads">
  </a>
</p>

<p align="center">
  <strong>A cross-platform cloud clipboard tool that supports real-time send of text, images, and files to cloud or local servers.</strong>
</p>

<p align="center">
  <a href="./docs/03-sync-usage-and-effects.md"><strong>🔄 Sync Usage and Effects Guide</strong></a>
  ·
  <a href="#-automatic-sync">Automatic Sync</a>
  ·
  <a href="#-quick-start">Quick Start</a>
</p>

## 📸 Screenshots

<details>
<summary><b>💻 Desktop</b></summary>

![Desktop Preview](https://ae01.alicdn.com/kf/Hfce3a9b69b3d404c8e3073ab0fffa913v.png)

</details>

<details>
<summary><b>📱 Mobile</b></summary>

![Mobile Preview](https://ae01.alicdn.com/kf/Hbf859dd0e42c4406bf94a6b6f2f4658cf.png)

</details>


---

## 🎯 Advantages

| Feature | Description |
|---------|-------------|
| 🔒 **Privacy** | Deploy locally or on your own server, full data control |
| 📦 **Easy Deploy** | Docker, Binary, Source code, Homebrew, OpenWrt, and more options |
| 🌍 **Cross-platform** | Windows, macOS, Linux, Android, iOS |
| ⚡ **Fast Sync** | Real-time synchronization, zero delay |
| 🔐 **Security** | Global password, room password, and sync device approval |
| 💾 **Flexible Storage** | Configurable history and file expiration |
| 🚀 **Lightweight** | Low resource usage, runs smoothly on low-end devices |
| 🔍 **Shortcuts** | Android/iOS shortcuts support |
| 🧩 **Sync Clients** | Web, desktop, and Android sync clients |

---

## Current Additions

This branch keeps the original web clipboard and file transfer flow, and adds the following sync-focused capabilities:

- **Independent sync protocol**: Adds `/sync/server`, `/sync/ws`, and `/api/sync/*` without replacing the legacy `/push` text/file flow.
- **Dual access gate**: Sync clients must pass the room/global password first, then wait for web-side device approval before becoming connected.
- **Web device management**: The device page supports sync device lists, pending / trusted states, device approval, status summaries, and auto-refreshing diagnostics.
- **Three-end text sync**: Web, desktop, and Android sync clients support plain-text clipboard synchronization with duplicate and loop prevention.
- **Desktop Go client**: `desktop-client-go/` replaces the earlier AHK path and provides tray mode, local control panel, hotkeys, shell menu, Tip notifications, file notice sending, latest text/file pull, and download cache cleanup.
- **Desktop UX guidance**: The control panel overview shows next-step guidance, connection diagnostics, cache directory, recent cleanup, platform capabilities, recent remote text sync, recent remote file notices, and the latest status refresh. Automatic reconnect stops after the configured limit and prompts manual inspection.
- **Android sync client**: `android-sync-client/` supports text sync, floating confirm for incoming images/files, confirm-before-download into private cache, and receive-page preview/open/share/save-as/mark-processed actions.
- **Android high-version guidance**: The runtime page provides clipboard readiness, background diagnostics, and a dynamic troubleshooting button for notification, accessibility, battery optimization, and vendor keepalive settings.
- **Cache lifecycle**: Android receive cache keeps items for 24 hours by default. Desktop download cache supports retention configuration and manual cleanup.

---

## 🔄 Automatic Sync

The new sync capability is an independent protocol separate from the legacy `/push` flow. It is intended for the “copy here, automatically appear on another device” workflow. The original web manual text send, file upload, and file download flow is still preserved.

Usage steps and expected behavior: [Sync Usage and Effects Guide](./docs/03-sync-usage-and-effects.md).

### Supported Scope

| Capability | Web | Desktop Go | Android Sync Client | Notes |
|------------|-----|------------|---------------------|-------|
| Plain-text auto sync | Supported | Supported | Supported | All three clients can publish local text changes and write remote text into the local clipboard |
| Device pairing approval | Manager | Client | Client | New devices enter pending first and can sync only after web-side approval |
| Room/global password | Supported | Supported | Supported | Clients pass room access validation before device approval |
| File/image notice sending | Supported | Supported | Not the primary sender | Web and desktop can upload files and send `payloadNotice` |
| File/image confirm receiving | No auto receive | No auto receive | Supported | Non-text automatic receive is currently Android-only and downloads only after user confirmation |
| Cache cleanup | Legacy file expiration | Download cache cleanup | Receive cache cleanup | Desktop and Android both provide local cache lifecycle handling |

### Client Entrypoints

- Web: open the server page and use the device / sync device management area to enable and approve sync devices.
- Desktop: use [`desktop-client-go/`](./desktop-client-go) for tray mode, control panel, hotkeys, shell menu, Tip notifications, and file notice sending.
- Android sync client: use [`android-sync-client/`](./android-sync-client) for text sync, floating confirmation for images/files, background clipboard diagnostics, and permission guidance.

### Android Sync Client Notes

- `android/` remains the original Android server app from the upstream project.
- `android-sync-client/` is the Android sync client and is intentionally kept separate from the server APK.
- Android text clipboard sync is most reliable in foreground scenarios. On Android 10+ and some vendor ROMs, background clipboard reads may be restricted by the system.
- The runtime page provides clipboard readiness, background diagnostics, and a dynamic troubleshooting button. The permission page provides common vendor background-keepalive guidance.
- Image/file receiving prefers floating confirmation. Downloads start only after user confirmation, then files can be previewed, opened, shared, saved as, or marked processed in the receive page.

### Boundaries

- Current automatic clipboard sync is plain text only. Rich text sync is not implemented.
- Automatic paste into third-party input fields is not implemented.
- Images/files are not automatically written into the system clipboard. Android also downloads them only after confirmation into the app private cache.
- Windows and Web do not currently auto-receive images/files. They keep file notice sending and manual download capabilities.

---

## 🚀 Quick Start

### 1️⃣ Docker (Recommended)

```bash
# Option 1: Docker Compose (Recommended)
docker compose up -d

# Option 2: Docker CLI
docker run -d \
  --name=cloud-clipboard-go \
  -p 9501:9501 \
  -e AUTH_PASSWORD='global-pass' \
  -e ROOM_AUTH_JSON='{"finance":"finance-pass","private":""}' \
  -v /path/to/data:/app/server-node/data \
  jonnyan404/cloud-clipboard-go
```

Then visit: `http://localhost:9501`

### 2️⃣ Binary Files

Download from [Releases](https://github.com/jonnyan404/cloud-clipboard-go/releases):

```bash
# Linux/macOS
./cloud-clipboard-go -port 9501

# Windows
cloud-clipboard-go.exe -port 9501
```

### 3️⃣ Android Application (Mobile Devices)

For deploying server directly on Android phone/tablet:

1. Download `.apk` file from [Releases](https://github.com/jonnyan404/cloud-clipboard-go/releases)
2. Install APK on your Android device
3. Open the app and set listening port (default 9501)
4. Set access password (optional)
5. Tap "Start Service"

Then access from other devices: `http://your-android-device-ip:9501`

**Advantages**:
- 📱 Run server directly on your phone without a computer
- 🚀 Ready to use, no additional dependencies
- 💾 Data persistence support

### 4️⃣ Homebrew (macOS)

```bash
brew install Jonnyan404/tap/cloud-clipboard-go
brew services start cloud-clipboard-go
```

### 5️⃣ OpenWrt (Router)

```bash
opkg update
opkg install cloud-clipboard-go_*_platform.ipk
opkg install cloud-clipboard-go_*_all.ipk
```

### 6️⃣ Build from Source

```bash
# Requirements: Node.js >= 22.12, Go >= 1.22

# 1. Build frontend and sync static assets
./build-web.ps1

# 2. Run backend
cd cloud-clip
go mod tidy
go run -tags embed .
```

Notes:

- `build-web.ps1` copies the latest frontend build into `cloud-clip/lib/static`.
- If you only update `client/dist` without syncing it to `cloud-clip/lib/static`, `go run -tags embed .` may still serve older frontend assets.
- To serve frontend assets from disk during development, run: `go run -tags embed . -static ../client/dist`.

### 7️⃣ Cloudflare Deployment

For cloud deployment scenarios, support one-click deployment to Cloudflare Workers + Pages:

```bash
# Requirements: Node.js >= 22.12, Wrangler CLI

# 1. Install Wrangler CLI
npm install -g wrangler

# 2. Login to Cloudflare
wrangler login

# 3. Run deployment script
cd cloudflare
./deploy.sh
```

**Deployment includes**:
- Cloudflare Workers (API backend)
- Cloudflare D1 (database)
- Cloudflare R2 (file storage)
- Cloudflare Pages (frontend interface)

**Advantages**:
- 🌐 Global CDN acceleration
- 🚀 No server maintenance required
- 💾 Automatic backup and scaling
- 🔒 Cloudflare security protection

**Important Notes**:
- Requires Cloudflare account
- Free tier usage (Workers: 100,000 requests/day, D1: 500MB storage, R2: 10GB storage)
- Access URL will be displayed after deployment

See: [Cloudflare Deployment Documentation](./cloudflare/README.md)

---

## 📋 Deployment Guide

### Docker Compose Configuration

Use the existing `docker-compose.yml` in the repository root as the source of truth. The container entrypoint generates the runtime config from these environment variables, so the documentation should match that file exactly.

Edit the root `docker-compose.yml` as needed:

```yaml
services:
  cloud-clipboard-go:
    container_name: cloud-clipboard-go
    restart: always
    ports:
      - "9501:9501"
    healthcheck:
      test: ["CMD-SHELL", "nc -z 127.0.0.1 \"${LISTEN_PORT:-9501}\" || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 10s
    environment:
      LISTEN_IP: ${LISTEN_IP:-} # Defaults to 0.0.0.0. You can set 127.0.0.1 if needed.
      LISTEN_IP6: ${LISTEN_IP6:-} # Defaults to empty. You can set :: for IPv6.
      LISTEN_PORT: ${LISTEN_PORT:-} # Defaults to 9501.
      PREFIX: ${PREFIX:-} # Subpath, for example /cloud-clipboard
      MESSAGE_NUM: ${MESSAGE_NUM:-} # History item count, defaults to 10.
      AUTH_PASSWORD: ${AUTH_PASSWORD:-} # Global access password, defaults to false.
      ROOM_AUTH_JSON: '${ROOM_AUTH_JSON:-{}}' # Room password JSON, for example {"finance":"finance-pass","ops":""}
      TEXT_LIMIT: ${TEXT_LIMIT:-} # Text length limit, defaults to 4096.
      FILE_EXPIRE: ${FILE_EXPIRE:-} # File expiration time in seconds, defaults to 3600.
      FILE_LIMIT: ${FILE_LIMIT:-} # File size limit in bytes, defaults to 104857600.
      MKCERT_DOMAIN_OR_IP: ${MKCERT_DOMAIN_OR_IP:-} # mkcert domain or IP. Multiple values can be separated by spaces.
      MANUAL_KEY_PATH: ${MANUAL_KEY_PATH:-} # Manual key path. Higher priority than MKCERT_DOMAIN_OR_IP.
      MANUAL_CERT_PATH: ${MANUAL_CERT_PATH:-} # Manual certificate path. Higher priority than MKCERT_DOMAIN_OR_IP.
      ROOM_LIST: ${ROOM_LIST:-} # Enable room list display, default is false.
      SYNC_STATE_CLEANUP: ${SYNC_STATE_CLEANUP:-} # Sync-state cleanup interval in seconds, default 600.
      SYNC_MESSAGE_EXPIRE: ${SYNC_MESSAGE_EXPIRE:-} # Sync text history retention in seconds, default 86400.
      SYNC_PAYLOAD_EXPIRE: ${SYNC_PAYLOAD_EXPIRE:-} # Sync payload notice retention in seconds, default 86400.
      SYNC_PENDING_DEVICE_EXPIRE: ${SYNC_PENDING_DEVICE_EXPIRE:-} # Offline pending device retention in seconds, default 604800.
      SYNC_TRUSTED_DEVICE_EXPIRE: ${SYNC_TRUSTED_DEVICE_EXPIRE:-} # Offline trusted device retention in seconds, default 0 disables auto-removal.
    volumes:
      - /path/your/dir/data:/app/server-node/data # Replace with your own directory
    image: jonnyan404/cloud-clipboard-go:latest
```

Run:

```bash
docker compose up -d
```

`ROOM_AUTH_JSON` must be a valid JSON object. An empty string value means that room falls back to `AUTH_PASSWORD`.

Additional notes:

- The documented Docker Compose variable name is `ROOM_AUTH_JSON`.
- The entrypoint still accepts the legacy variable name `ROOM_AUTH` for backward compatibility, but the Compose example and docs are now standardized on `ROOM_AUTH_JSON`.
- If you use a `.env` file, keep the same variable names and only fill in the values.
- The image explicitly installs `nc`, and the health check only verifies that the container is listening on the configured port, independent of `PREFIX` and HTTP/HTTPS settings.
- The `SYNC_*` variables only affect the new phase-one sync state file `sync-state.json`; they do not change the legacy `/push` flow.
- `SYNC_PENDING_DEVICE_EXPIRE` is useful for pruning long-unapproved devices. `SYNC_TRUSTED_DEVICE_EXPIRE` defaults to `0` so approved devices are not removed unexpectedly.

Example:

```yaml
environment:
  AUTH_PASSWORD: 'global-pass'
  ROOM_AUTH_JSON: '{"finance":"finance-pass","private":""}'
```

To change `roomAuth` dynamically through variables, the recommended approach is to use a `.env` file:

```env
AUTH_PASSWORD=global-pass
ROOM_AUTH_JSON={"finance":"finance-pass","private":""}
```

Then run:

```bash
docker compose up -d
```

You can also override it for a single run:

```bash
ROOM_AUTH_JSON='{"finance":"new-pass","ops":"ops-pass"}' docker compose up -d
```

Note: the Docker image only auto-generates config when [cloud-clip/config.json](cloud-clip/config.json) does not exist. If you already mounted an existing `config.json`, changing environment variables will not rewrite it automatically. In that case, delete the file and recreate the container, or edit `server.roomAuth` manually.

### Binary Command-line Parameters

```bash
# Priority: Command-line > Config file > Default values

-host string
    Server listening address (default "0.0.0.0")

-port int
    Server listening port (default 9501)

-auth string
    Access password

-config string
    Configuration file path

-static string
    External frontend file path
```

Example:

```bash
./cloud-clipboard-go -host 127.0.0.1 -port 8080 -auth mypassword123
```

---

## 📱 Client Usage

### 📲 Android Shortcuts

1. Download [HTTP Shortcuts](https://github.com/Waboodoo/HTTP-Shortcuts/releases)
2. Download [Shortcuts file](https://raw.githubusercontent.com/jonnyan404/cloud-clipboard-go/refs/heads/main/shortcuts/cloud-clipboard-shortcuts.zip)
3. Import into HTTP Shortcuts
4. Configure variables:
   - `url`: Your server address (e.g., `http://192.168.1.100:9501`)
   - `room`: Room name (optional)
   - `auth`: Authentication password (optional)

### 🖥️ Desktop Application

The currently maintained desktop sync client lives in:

- [`desktop-client-go/`](./desktop-client-go)

Current capabilities:

- Local control panel grouped by `Overview / Connection / Actions / Advanced`.
- Overview cards for next-step guidance, connection diagnostics, cache directory, recent cleanup, and platform capabilities.
- Windows tray, global hotkeys, shell context menu, file notice sending, latest text/file pull, and download cache cleanup.
- Notification modes: corner Tip, system notification, log-only, or off. Tip supports theme, size, and remembered position.
- Automatic reconnect attempts stop after the configured limit and then guide the user to check the service manually.

### 💻 UI Launcher Tool

Download [Cloud Clipboard Go Launcher](https://github.com/jonnyan404/cloud-clipboard-go-launcher/releases) - no command line needed.

---

## 🌐 API Endpoints

### Get Latest Content

```bash
GET /content/latest
```

Returns the latest clipboard content.

**Parameters**:
- `room` (optional): Room name

**Examples**:

```bash
curl http://localhost:9501/content/latest
curl http://localhost:9501/content/latest?room=work
```

API and configuration guide: [cloud-clip/config.md](./cloud-clip/config.md)

---

## 🐳 Docker Images

### Image Sources

| Source | Repository |
|--------|------------|
| Docker Hub | `jonnyan404/cloud-clipboard-go` |
| GitHub Container Registry | `ghcr.io/jonnyan404/cloud-clipboard-go` |

### Pull Latest Image

```bash
docker pull jonnyan404/cloud-clipboard-go:latest
```

---

## 📚 Documentation

- 📖 [Configuration Guide](./cloud-clip/config.md)
- 🔌 [API and Configuration Guide](./cloud-clip/config.md)
- 📱 [Client Deployment](#-client-usage)
- 🔄 [Sync Usage and Effects Guide](./docs/03-sync-usage-and-effects.md)

---

## 🔄 Supported Platforms

| Platform | Binary | Docker | Source | Notes |
|----------|--------|--------|--------|-------|
| Linux | ✅ | ✅ | ✅ | Primary support |
| macOS | ✅ | ✅ | ✅ | Intel/Apple Silicon |
| Windows | ✅ | ✅ | ✅ | Requires Visual C++ Build Tools |
| Android | ✅ | - | ✅ | Server APK/Shortcuts |
| iOS | - | - | - | Shortcuts |
| OpenWrt | ✅ | - | ✅ | Router systems |

---

## 🐛 Troubleshooting

### Docker Container Won't Start

```bash
# Check logs
docker logs cloud-clipboard-go

# Check if port is in use
netstat -tuln | grep 9501

# Restart container
docker restart cloud-clipboard-go
```

### Can't Access Web Interface

- Check firewall isn't blocking port 9501
- Verify container is running: `docker ps | grep cloud-clipboard-go`
- Try local access: `http://localhost:9501`

### File Upload Fails

- Check disk space availability
- Verify `FILE_LIMIT` environment variable setting
- Ensure data directory is writable: `chmod 777 ./data`

## 📦 Related Projects

- **[Cloud Clipboard Go Launcher](https://github.com/jonnyan404/cloud-clipboard-go-launcher)** - UI launcher tool for easier usage

---

## 🙏 Acknowledgments

This project is based on:

- [TransparentLC/cloud-clipboard](https://github.com/TransparentLC/cloud-clipboard)
- [yurenchen000/cloud-clipboard](https://github.com/yurenchen000/cloud-clipboard)

---

## 📊 Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Jonnyan404/cloud-clipboard-go&type=Date)](https://www.star-history.com/#Jonnyan404/cloud-clipboard-go&Date)

---

## 📄 License

MIT License - See [LICENSE](LICENSE) for details

---

## 💬 Community & Feedback

- 📝 Report [Issues](https://github.com/jonnyan404/cloud-clipboard-go/issues)
- 🔀 Submit [Pull Requests](https://github.com/jonnyan404/cloud-clipboard-go/pulls)
- 💡 Join [Discussions](https://github.com/jonnyan404/cloud-clipboard-go/discussions)

---

**Last Updated**: June 6, 2026 | 📖 [中文版本](README.md)
