# Cloud Clipboard Desktop Go Client

这是当前电脑端主线客户端，已替代旧的 AHK 方案，围绕 Windows 桌面场景提供一期同步能力与本地控制面板。

## 当前能力

- 托盘常驻启动，左键打开控制面板，右键菜单可执行常用动作
- 连接 Go 服务端 `/sync/server` 与 `/sync/ws`
- 使用 `roomPassword + 网页端设备批准` 双层校验
- 纯文本剪贴板自动同步，收到远端文本后自动写回系统剪贴板
- 未获批设备保持连接但不发送同步内容
- 本地 `state.json` 持久化连接状态、最近远端内容、最近动作
- 支持通知模式：`tip / popup / log / off`
- `tip` 模式支持主题切换、尺寸设置、拖动定位、位置记忆
- 支持成功提示开关，默认开启；直接动作成功后会替换旧 Tip
- 内置本地控制面板，按 `概览 / 连接 / 动作 / 高级` 分组收口配置
- 支持失败自动重连次数与重连间隔控制，超过上限后暂停自动重连，等待手动重连
- 支持启动时自动打开控制面板
- 支持选择文件上传并广播文件通知
- 支持手动发送输入文本或当前剪贴板文本
- 支持主动拉取服务端最新文本到本机剪贴板
- 支持下载最新文件到本地目录
- Windows 支持把最新文件直接拉到本机文件剪贴板，下载后可直接在资源管理器 `Ctrl+V`
- 支持打开下载目录、手动清空下载缓存、按保留时长自动清理过期缓存
- 支持全局热键：发送剪贴板文本、拉取最新文本、拉取最新文件到剪贴板、下载最新文件
- 支持 Windows 右键子菜单：
  - 文件：`复制到剪贴板服务器`
  - 目录/空白处：`从剪贴板服务器粘贴到此处`
  - 目录/空白处：`拉取最新文件到剪贴板`
- 支持检测新的剪贴板文件列表，先进入待确认状态
- Windows 下待确认剪贴板文件可通过右下角 Tip、托盘菜单、控制面板确认发送

## 运行

```powershell
cd E:\Workspace\VSCode\cloud-clipboard\desktop-client-go
go run ./cmd/cloud-clipboard-desktop
```

首次运行会自动生成 `config.json`。

如果只想无托盘运行，方便调试：

```powershell
cd E:\Workspace\VSCode\cloud-clipboard\desktop-client-go
go run ./cmd/cloud-clipboard-desktop -headless
```

## 控制面板

默认地址：`http://127.0.0.1:9530/`

面板可配置：

- `serverBase`
- `room`
- `roomPassword`
- `deviceName`
- `noticeMode`
- `pollIntervalMs`
- `reconnectDelayMs`
- `maxReconnectAttempts`
- `openPanelOnLaunch`
- `panelAddress`
- `downloadDir`
- `downloadCacheRetentionHours`
- `clipboardFileConfirmEnabled`
- `clipboardFileConfirmWindowSec`
- `sendClipboardHotkey`
- `fetchLatestHotkey`
- `fetchLatestFileHotkey`
- `downloadLatestHotkey`
- `shellMenuEnabled`
- `tipWidth`
- `tipHeight`
- `tipTheme`
- `tipLeft`
- `tipTop`
- `successNoticeEnabled`

保存后会立即触发同步客户端重连。

如果修改了 `panelAddress`，需要用新地址重新打开页面。

## 右键动作

可执行文件支持以下一次性参数，供 Windows 右键菜单调用：

```powershell
-shell-send <file-path>
-shell-download-dir <dir-path>
-shell-fetch-latest-file
```

对应行为：

- `-shell-send`：发送指定文件到同步房间
- `-shell-download-dir`：把服务端最新文件下载到指定目录
- `-shell-fetch-latest-file`：把服务端最新文件下载到缓存并写入本机文件剪贴板

## 本地文件

- `config.json`：客户端配置
- `state.json`：连接状态与最近动作
- `downloads/` 或自定义下载目录：拉取的文件缓存

## 说明

- 当前桌面端主收口平台是 Windows
- Linux / macOS 仍以基础兼容为主，完整桌面体验后续再补
- Android 同步客户端在仓库独立目录 `android-sync-client/`
