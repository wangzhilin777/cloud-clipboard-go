# Cloud Clipboard Desktop Go Client

这是当前电脑端主线客户端，已替代旧的 AHK 方案，围绕 Windows 桌面场景提供一期同步能力与本地控制面板。

如果你想直接看更完整的使用说明、配置说明和常见问题，先读 [桌面端使用说明](../docs/15-desktop-client-guide.md)。

## 当前能力

- 托盘常驻启动，左键优先唤起控制面板窗口，右键菜单可执行常用动作
- 额外提供无托盘面板入口 `cloud-clipboard-panel`，适合不需要托盘或系统策略拦截托盘组件时使用
- 连接 Go 服务端 `/sync/server` 与 `/sync/ws`
- 使用 `roomPassword + 网页端设备批准` 双层校验
- 纯文本剪贴板自动同步，收到远端文本后自动写回系统剪贴板
- 未获批设备保持连接但不发送同步内容
- 本地 `state.json` 持久化连接状态、最近远端内容、最近动作
- 支持通知模式：`tip / popup / log / off`
- `tip` 模式支持主题切换、尺寸设置、拖动定位、位置记忆
- `tip` 模式支持右下角热角唤出、自动关闭时长、拖拽上传和位置记忆
- 支持成功提示开关，默认开启；直接动作成功后会替换旧 Tip
- 内置本地控制面板，按 `概览 / 连接 / 动作 / 高级` 分组收口配置
- 控制面板概览页已补充“下一步建议 / 连接诊断 / 缓存与目录 / 平台能力 / 最近远端文本同步 / 最近远端文件通知 / 状态最近刷新”摘要，便于直接识别回环地址、待批准、自动重连暂停、文本是否回流、最近通知时间、下载缓存位置，以及当前平台支持的文件选择、文件剪贴板和右键菜单能力
- 支持失败自动重连次数与重连间隔控制，超过上限后暂停自动重连，等待手动重连
- 支持启动时自动打开控制面板
- 支持选择文件上传并广播文件通知
- 支持手动发送输入文本或当前剪贴板文本
- 支持主动拉取服务端最新文本到本机剪贴板
- 支持下载最新文件到本地目录
- Windows 支持把最新文件直接拉到本机文件剪贴板，下载后可直接在资源管理器 `Ctrl+V`
- 支持打开下载目录、手动清空下载缓存、按保留时长自动清理过期缓存
- 支持全局热键：打开控制面板、发送剪贴板文本、拉取最新文本、拉取最新文件到剪贴板、下载最新文件
- 支持 Windows 右键子菜单：
  - 文件：`复制到剪贴板服务器`
  - 目录/空白处：`从剪贴板服务器粘贴到此处`
  - 目录/空白处：`拉取最新文件到剪贴板`
- Windows 右键一次性动作会按当前通知模式返回成功或失败提示
- 支持检测新的剪贴板文件列表，先进入待确认状态
- Windows 下待确认剪贴板文件可通过右下角 Tip、托盘菜单、控制面板确认发送

## 运行

```powershell
cd E:\Workspace\VSCode\cloud-clipboard\desktop-client-go
go run ./cmd/cloud-clipboard-desktop
```

首次运行会自动生成 `config.json`。

打包成 exe 后直接双击运行时，默认会在 exe 同目录读取或生成 `config.json`；`go run` 开发运行时则使用当前命令所在目录。也可以复制 `config.example.json` 为 `config.json` 后再启动，留空的设备名、设备 ID 和下载目录会在首次加载时自动补齐并写回。

如果只想无托盘运行，方便调试：

```powershell
cd E:\Workspace\VSCode\cloud-clipboard\desktop-client-go
go run ./cmd/cloud-clipboard-desktop -headless
```

如果当前系统策略拦截托盘组件，可直接运行无托盘面板版：

```powershell
cd E:\Workspace\VSCode\cloud-clipboard\desktop-client-go
go run ./cmd/cloud-clipboard-panel
```

`cloud-clipboard-panel` 不加载托盘组件，但仍会启动本地控制面板、同步连接、文件发送、最新文本 / 文件拉取、右键一次性动作等核心能力。

## 控制面板

默认地址：`http://127.0.0.1:9530/`

概览页当前会直接展示：

- 连接状态
- 下一步建议
- 最近远端文本同步
- 最近远端文件通知
- 最近错误
- 待确认剪贴板文件
- 最近缓存清理
- 状态最近刷新
- 连接诊断
- 缓存与目录摘要

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
- `openPanelHotkey`
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
- `tipAutoCloseSec`
- `tipHotCornerEnabled`
- `successNoticeEnabled`

保存后会立即触发同步客户端重连。

如果修改了 `panelAddress`，需要用新地址重新打开页面。

## 右键动作

托盘版和无托盘面板版都支持以下一次性参数，供 Windows 右键菜单调用：

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
- `config.example.json`：可复制的干净配置模板，不包含本机设备 ID 和个人路径
- `state.json`：连接状态与最近动作
- `downloads/` 或自定义下载目录：拉取的文件缓存

## 说明

- 桌面端优先支持 Windows
- Linux / macOS 已支持基础文件选择、文件上传和下载后写入文件剪贴板；Linux 桌面环境需要可用的 `zenity`、`kdialog` 或 `yad`，文件剪贴板需要 `wl-copy` 或 `xclip`
- Android 同步客户端在仓库独立目录 `android-sync-client/`
- Windows 打包图标统一使用 `desktop-client-go/internal/tray/assets/cloud-clipboard-desktop.ico`，重新打包时请保留并继续使用这份资源再生成 `rsrc_windows_amd64.syso`

## 常见说明

- 想控制右下角提示多久自动消失，就改 `tipAutoCloseSec`
- 想关掉拖到右下角自动弹窗，就关 `tipHotCornerEnabled`
- 不想要托盘时，直接运行 `cloud-clipboard-panel`
- 想快速查配置字段、使用流程和常见问题，优先看 [桌面端使用说明](../docs/15-desktop-client-guide.md)

