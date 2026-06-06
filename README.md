<h1 align="center">云剪贴板 Cloud Clipboard Go</h1>

<p align="center">
  <a href="README.en.md"><img src="https://img.shields.io/badge/lang-English-blue.svg" alt="English Readme"></a>
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
  <strong>局域网云剪贴板，支持网页、Windows、Android 多端文本同步与文件中转。</strong>
</p>

<p align="center">
  自建可控、房间隔离、支持密码访问，适合个人与家庭设备之间快速同步剪贴板内容。
</p>

<p align="center">
  <a href="./docs/03-sync-usage-and-effects.md"><strong>🔄 自动同步使用与效果说明</strong></a>
  ·
  <a href="#-自动同步能力">自动同步能力</a>
  ·
  <a href="#-快速开始">快速开始</a>
</p>

---

## 📸 截图预览

<details>
<summary><b>💻 桌面端</b></summary>

![Desktop Preview](https://ae01.alicdn.com/kf/Hfce3a9b69b3d404c8e3073ab0fffa913v.png)

</details>

<details>
<summary><b>📱 移动端</b></summary>

![Mobile Preview](https://ae01.alicdn.com/kf/Hbf859dd0e42c4406bf94a6b6f2f4658cf.png)

</details>

<details>
<summary><b>📡 路由器</b></summary>

![OpenWrt Preview](https://github.com/Jonnyan404/cloud-clipboard-go/blob/main/openwrt/demo.png)

</details>

---

## 🎯 优势特性

| 特性 | 说明 |
|------|------|
| 🔒 **隐私安全** | 可部署在本地或自有服务器，数据完全可控 |
| 📦 **易于部署** | 支持 Docker、源代码、二进制、Homebrew、OpenWrt等多种方式 |
| 🌍 **跨平台** | 支持 Windows、macOS、Linux、Android、iOS |
| ⚡ **高效同步** | 实时同步，无延迟 |
| 🔐 **认证保护** | 支持全局密码、房间密码和同步设备批准 |
| 💾 **灵活存储** | 支持配置历史记录和文件过期时间 |
| 🚀 **轻量高效** | 资源占用少，即使在低配设备也能流畅运行 |
| 🔍 **快捷指令** | Android/iOS 快捷指令支持 |
| 🧩 **同步客户端** | 支持网页、桌面端、Android 同步客户端接入 |

---

## 当前新增能力

当前版本在原有网页剪贴板和文件中转能力基础上，新增并收口了以下能力：

- **独立同步协议**：新增 `/sync/server`、`/sync/ws` 与 `/api/sync/*`，和旧 `/push` 文本/文件链路分离。
- **双重接入校验**：同步客户端需要先通过房间密码或全局密码，再由网页端批准设备后才进入已连接状态。
- **网页端同步管理**：设备页支持同步设备列表、pending / trusted 状态、批准设备、状态摘要和同步诊断自动刷新。
- **三端文本同步**：网页端、Windows 桌面端、Android 同步客户端支持纯文本剪贴板自动同步，并带去重和防回环处理。
- **桌面端 Go 客户端**：`desktop-client-go/` 已替代早期 AHK 方案，支持托盘、控制面板、热键、右键菜单、Tip 提示、文件通知发送、最新文本/文件拉取、下载缓存清理。
- **桌面端体验收口**：控制面板概览页提供下一步建议、连接诊断、缓存目录、最近缓存清理和平台能力摘要；自动重连达到上限后会暂停并提示手动检查。
- **Android 同步客户端**：`android-sync-client/` 支持文本同步、悬浮确认接收图片/文件、确认后下载到私有缓存、接收页预览/打开/分享/另存为/标记已处理。
- **Android 高版本引导**：运行页提供后台复制就绪度、后台复制诊断和动态排查按钮，可直达通知、无障碍、电池优化和厂商后台保活设置。
- **缓存生命周期**：Android 接收缓存默认保留 24 小时，桌面端下载缓存支持保留时长配置和手动清理。

---

## 🔄 自动同步能力

新增同步能力是独立于旧 `/push` 的一套协议，适合需要“复制后自动到另一台设备剪贴板”的场景。旧网页手动发送文本、上传文件、下载文件能力仍保留。

详细使用步骤和效果说明见：[自动同步使用与效果说明](./docs/03-sync-usage-and-effects.md)。

### 支持范围

| 能力 | 网页端 | 桌面端 Go | Android 同步客户端 | 说明 |
|------|--------|------------|---------------------|------|
| 纯文本自动同步 | 支持 | 支持 | 支持 | 三端都支持复制文本后自动上报，收到远端文本后写入本机剪贴板 |
| 设备配对批准 | 管理端 | 接入端 | 接入端 | 设备先进入待批准，网页端批准后才可正式同步 |
| 房间密码 / 全局密码 | 支持 | 支持 | 支持 | 先通过房间访问校验，再进入设备批准流程 |
| 文件 / 图片通知发送 | 支持 | 支持 | 暂不作为发送主入口 | 网页端和桌面端可上传文件后发送 `payloadNotice` |
| 文件 / 图片确认接收 | 不做自动接收 | 不做自动接收 | 支持 | 当前非文本自动接收只在 Android 端实现，且必须用户确认后才下载 |
| 缓存清理 | 旧文件过期清理 | 下载缓存清理 | 接收缓存清理 | 桌面端和 Android 端都有本地缓存生命周期配置或触发器 |

### 客户端入口

- 网页端：打开服务端页面后，在“设备 / 同步设备管理”中启用和批准同步设备。
- 桌面端：使用 [`desktop-client-go/`](./desktop-client-go)，支持托盘、控制面板、热键、右键菜单、Tip 提示和文件通知发送。
- Android 同步客户端：使用 [`android-sync-client/`](./android-sync-client)，支持文本同步、悬浮确认接收图片/文件、后台复制诊断和权限引导。

### Android 同步客户端说明

- `android/` 仍表示原仓库自带的 Android 服务器端 App。
- `android-sync-client/` 是新增的 Android 同步客户端，不与服务器 APK 混用。
- Android 文本剪贴板同步以前台场景最可靠；在 Android 10+ 或部分 ROM 上，后台读取系统剪贴板可能被系统限制。
- 当前运行页已提供后台复制就绪度摘要、后台复制诊断卡片和动态排查按钮，权限页已提供常见厂商后台保活入口与建议。
- 图片/文件接收优先走悬浮确认，用户确认后才下载到应用私有缓存，并可在接收页预览、打开、分享、另存为或标记已处理。

### 边界说明

- 当前只做纯文本自动写入系统剪贴板，不做富文本自动同步。
- 当前不做第三方输入框自动粘贴。
- 当前图片/文件不会自动写入系统剪贴板，Android 端也必须确认后下载到应用私有缓存。
- Windows / Web 当前不做图片/文件自动接收，只保留发送通知和手动下载能力。

---

## 🚀 快速开始

### 1️⃣ 使用 Docker（最推荐）

```bash
# 方式一：Docker Compose（推荐）
docker compose up -d

# 方式二：Docker 命令行
docker run -d \
  --name=cloud-clipboard-go \
  -p 9501:9501 \
  -e AUTH_PASSWORD='global-pass' \
  -e ROOM_AUTH_JSON='{"finance":"finance-pass","private":""}' \
  -v /path/to/data:/app/server-node/data \
  jonnyan404/cloud-clipboard-go
```

然后访问：`http://localhost:9501`

### 2️⃣ 使用二进制文件

前往 [Releases](https://github.com/jonnyan404/cloud-clipboard-go/releases) 下载对应平台的文件：

```bash
# Linux/macOS
./cloud-clipboard-go -port 9501

# Windows
cloud-clipboard-go.exe -port 9501
```

### 3️⃣ 使用 Android 应用（移动设备）

对于在 Android 手机/平板上直接部署服务器的场景：

1. 前往 [Releases](https://github.com/jonnyan404/cloud-clipboard-go/releases) 下载 `.apk` 文件
2. 在 Android 设备上安装 APK
3. 打开应用，设置监听端口（默认 9501）
4. 设置访问密码（可选）
5. 点击"启动服务"

然后在其他设备访问：`http://你的安卓设备IP:9501`

**优点**：
- 📱 无需电脑，在手机上直接运行服务器
- 🚀 开箱即用，无需额外依赖
- 💾 支持数据持久化

### 4️⃣ 使用 Homebrew（macOS）

```bash
brew install Jonnyan404/tap/cloud-clipboard-go
brew services start cloud-clipboard-go
```

### 5️⃣ 使用 OpenWrt（路由器）

```bash
opkg update
opkg install cloud-clipboard-go_*_platform.ipk
opkg install cloud-clipboard-go_*_all.ipk
```

### 6️⃣ 从源代码构建

```bash
# 前置要求：Node.js >= 22.12、Go >= 1.22

# 1. 构建前端并同步静态资源
./build-web.ps1

# 2. 运行后端
cd cloud-clip
go mod tidy
go run -tags embed .
```

说明：
- `build-web.ps1` 会在 `npm run build` 后自动把最新前端产物同步到 `cloud-clip/lib/static`
- 如果只更新了 `client/dist`，但没有同步到 `cloud-clip/lib/static`，那么 `go run -tags embed .` 仍可能带着旧前端启动，出现页面资源 hash 不匹配
- 若希望运行时始终直接读取磁盘静态目录，也可以使用：`go run -tags embed . -static ../client/dist`

### 7️⃣ 使用 Cloudflare（云端部署）

对于需要云端部署的场景，支持一键部署到 Cloudflare Workers + Pages：

```bash
# 前置要求：Node.js >= 22.12、Wrangler CLI

# 1. 安装 Wrangler CLI
npm install -g wrangler

# 2. 登录 Cloudflare
wrangler login

# 3. 执行部署脚本
cd cloudflare
./deploy.sh
```

**部署包含**：
- Cloudflare Workers (API 后端)
- Cloudflare D1 (数据库)
- Cloudflare R2 (文件存储)
- Cloudflare Pages (前端界面)

**优点**：
- 🌐 全球 CDN 加速
- 🚀 无需服务器维护
- 💾 自动备份和扩展
- 🔒 Cloudflare 安全防护

**注意事项**：
- 需要 Cloudflare 账号
- 免费额度内使用（Workers: 100,000 请求/天，D1: 500MB 存储，R2: 10GB 存储）
- 部署完成后会显示访问 URL

详见：[Cloudflare 部署文档](./cloudflare/README.md)

---

## 📋 部署指南

### Docker Compose 配置

以仓库根目录现有的 `docker-compose.yml` 为准。镜像启动时会由入口脚本按这些环境变量生成配置文件，因此文档中的变量写法也应与它保持一致。

按需修改根目录 `docker-compose.yml`：

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
      LISTEN_IP: ${LISTEN_IP:-} #默认为0.0.0.0,可设置为 127.0.0.1 不懂勿动
      LISTEN_IP6: ${LISTEN_IP6:-} #默认为空,ipv6地址,可设置为::,不懂勿动
      LISTEN_PORT: ${LISTEN_PORT:-} #默认为9501,可设置为其他端口
      PREFIX: ${PREFIX:-} #子路径,可配合nginx使用,格式: /cloud-clipboard
      MESSAGE_NUM: ${MESSAGE_NUM:-} #历史记录的数量,默认为10
      AUTH_PASSWORD: ${AUTH_PASSWORD:-} #访问密码,默认为false,可自定义字符串密码
      ROOM_AUTH_JSON: '${ROOM_AUTH_JSON:-{}}' #房间密码JSON, 例如 {"finance":"finance-pass","ops":""}
      TEXT_LIMIT: ${TEXT_LIMIT:-} #文本长度限制,默认为4096(2048个汉字),可设置为其他长度
      FILE_EXPIRE: ${FILE_EXPIRE:-} #文件过期时间,默认为3600(1小时),可设置为其他时间,单位为秒
      FILE_LIMIT: ${FILE_LIMIT:-} #文件大小限制,默认为104857600(100MB),可设置为其他大小,单位为字节
      MKCERT_DOMAIN_OR_IP: ${MKCERT_DOMAIN_OR_IP:-} #mkcert域名或IP,默认为空,可设置为其他域名或IP,多个用空格分隔,仅域名支持通配符*
      MANUAL_KEY_PATH: ${MANUAL_KEY_PATH:-} #手动设置证书路径,默认为空,该参数优先级高于MKCERT_DOMAIN_OR_IP
      MANUAL_CERT_PATH: ${MANUAL_CERT_PATH:-} #手动设置证书路径,默认为空,该参数优先级高于MKCERT_DOMAIN_OR_IP
      ROOM_LIST: ${ROOM_LIST:-} #是否启用房间列表展示功能,默认false
      SYNC_STATE_CLEANUP: ${SYNC_STATE_CLEANUP:-} #同步状态清理周期,默认600秒
      SYNC_MESSAGE_EXPIRE: ${SYNC_MESSAGE_EXPIRE:-} #同步文本历史保留秒数,默认86400
      SYNC_PAYLOAD_EXPIRE: ${SYNC_PAYLOAD_EXPIRE:-} #同步payload通知保留秒数,默认86400
      SYNC_PENDING_DEVICE_EXPIRE: ${SYNC_PENDING_DEVICE_EXPIRE:-} #pending设备离线保留秒数,默认604800
      SYNC_TRUSTED_DEVICE_EXPIRE: ${SYNC_TRUSTED_DEVICE_EXPIRE:-} #trusted设备离线保留秒数,默认0表示不自动移除
    volumes:
      - /path/your/dir/data:/app/server-node/data #请注意修改为你自己的目录
    image: jonnyan404/cloud-clipboard-go:latest
```

运行：

```bash
docker compose up -d
```

`ROOM_AUTH_JSON` 需要是合法的 JSON 对象，值为空字符串时表示该房间沿用 `AUTH_PASSWORD`。

补充说明：

- 当前 Docker Compose 文档变量名以 `ROOM_AUTH_JSON` 为准。
- 入口脚本仍兼容旧变量名 `ROOM_AUTH`，但 Compose 示例和后续文档统一使用 `ROOM_AUTH_JSON`。
- 如果你使用 `.env` 文件，建议保持与上面的 `${VAR:-}` 模板对应，只填写右侧的实际值。
- 镜像内显式安装了 `nc`，Compose 健康检查只检查容器内监听端口，和 `PREFIX`、HTTP/HTTPS 配置无关。
- `SYNC_*` 变量只作用于新增的一期同步协议状态文件 `sync-state.json`，不会改写旧 `/push` 逻辑。
- `SYNC_PENDING_DEVICE_EXPIRE` 适合清理长期未批准设备；`SYNC_TRUSTED_DEVICE_EXPIRE` 默认为 `0`，避免已批准设备被误清。

示例：

```yaml
environment:
  AUTH_PASSWORD: 'global-pass'
  ROOM_AUTH_JSON: '{"finance":"finance-pass","private":""}'
```

如果你想通过变量动态修改 `roomAuth`，推荐配合 `.env` 文件：

```env
AUTH_PASSWORD=global-pass
ROOM_AUTH_JSON={"finance":"finance-pass","private":""}
```

然后执行：

```bash
docker compose up -d
```

也可以临时覆盖：

```bash
ROOM_AUTH_JSON='{"finance":"new-pass","ops":"ops-pass"}' docker compose up -d
```

注意：Docker 镜像启动时只会在不存在 [cloud-clip/config.json](cloud-clip/config.json) 时自动生成配置。若你已挂载旧的 `config.json`，修改环境变量后需要删除该文件重建，或直接手动修改其中的 `server.roomAuth`。

### 二进制文件参数

```bash
# 参数优先级：命令行 > 配置文件 > 默认值

-host string
    服务器监听地址 (默认 "0.0.0.0")

-port int
    服务器监听端口 (默认 9501)

-auth string
    访问密码

-config string
    配置文件路径

-static string
    外部前端文件路径
```

示例：

```bash
./cloud-clipboard-go -host 127.0.0.1 -port 8080 -auth mypassword123
```

---

## 📱 客户端使用

### 📲 Android 快捷指令

1. 下载 [HTTP Shortcuts](https://github.com/Waboodoo/HTTP-Shortcuts/releases)
2. 下载 [快捷指令文件](https://raw.githubusercontent.com/jonnyan404/cloud-clipboard-go/refs/heads/main/shortcuts/cloud-clipboard-shortcuts.zip)
3. 在 HTTP Shortcuts 中导入文件
4. 配置变量：
   - `url`: 你的服务器地址 (如：`http://192.168.1.100:9501`)
   - `room`: 房间名称（可选）
   - `auth`: 认证密码（可选）

### 🖥️ 桌面端应用

仓库内当前实际维护的桌面同步客户端请见：

- [`desktop-client-go/`](./desktop-client-go)

当前已具备：

- 本地控制面板，按 `概览 / 连接 / 动作 / 高级` 分组管理
- 控制面板概览页可直接查看下一步建议、连接诊断、缓存目录、最近缓存清理与平台能力
- Windows 托盘、全局热键、右键菜单、文件通知发送、拉取最新文本/文件、下载缓存清理
- 支持右下角 Tip / 系统通知 / 日志 / 关闭通知等提示模式，Tip 支持主题、尺寸和位置记忆
- 支持失败自动重连上限，超过次数后暂停重连并提示手动检查

### 💻 UI 辅助工具

下载 [Cloud Clipboard Go Launcher](https://github.com/jonnyan404/cloud-clipboard-go-launcher/releases)，无需命令行操作。

---

## 🌐 API 接口

### 获取最新内容

```bash
GET /content/latest
```

返回最新的一条剪贴板内容。

**参数**：
- `room` (可选)：房间名称

**示例**：

```bash
curl http://localhost:9501/content/latest
curl http://localhost:9501/content/latest?room=work
```

接口与配置说明：[config.md](./cloud-clip/config.md)

---

## 🐳 Docker 镜像

### 镜像来源

| 来源 | 仓库 |
|------|------|
| Docker Hub | `jonnyan404/cloud-clipboard-go` |
| GitHub Container Registry | `ghcr.io/jonnyan404/cloud-clipboard-go` |

### 拉取最新镜像

```bash
docker pull jonnyan404/cloud-clipboard-go:latest
```

---

## 📚 详细文档

- 📖 [配置文件说明](./cloud-clip/config.md)
- 🔌 [接口与配置说明](./cloud-clip/config.md)
- 📱 [客户端部署指南](#-客户端使用)
- 🔄 [自动同步使用与效果说明](./docs/03-sync-usage-and-effects.md)

---

## 🔄 支持的平台

| 平台 | 二进制 | Docker | 源代码 | 说明 |
|------|---------|--------|--------|------|
| Linux | ✅ | ✅ | ✅ | 主要支持 |
| macOS | ✅ | ✅ | ✅ | Intel/Apple Silicon |
| Windows | ✅ | ✅ | ✅ | 需要 Visual C++ Build Tools |
| Android | ✅ | - | ✅ | 服务端APK/快捷指令 |
| iOS | - | - | - | 快捷指令 |
| OpenWrt | ✅ | ✅ | ✅ | 路由器系统 |

---


## 📦 衍生项目

- **[Cloud Clipboard Go Launcher](https://github.com/jonnyan404/cloud-clipboard-go-launcher)** - UI 辅助工具，方便不使用终端的用户
---

## 🙏 致谢

本项目前端(client)和后端(cloud-clip) fork以下开源项目修改而来：

- [TransparentLC/cloud-clipboard](https://github.com/TransparentLC/cloud-clipboard)
- [yurenchen000/cloud-clipboard](https://github.com/yurenchen000/cloud-clipboard)

---

## 📊 Star 历史

[![Star History Chart](https://api.star-history.com/svg?repos=Jonnyan404/cloud-clipboard-go&type=Date)](https://www.star-history.com/#Jonnyan404/cloud-clipboard-go&Date)

---

## 📄 许可证

MIT License - 详见 [LICENSE](LICENSE)


## 💬 交流反馈

- 📝 提交 [Issues](https://github.com/jonnyan404/cloud-clipboard-go/issues)
- 🔀 贡献 [Pull Requests](https://github.com/jonnyan404/cloud-clipboard-go/pulls)
- 💡 讨论 [Discussions](https://github.com/jonnyan404/cloud-clipboard-go/discussions)

---

**最后更新**: 2026年6月6日 | 📖 [English Version](README.en.md)

