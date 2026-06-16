# 桌面端使用说明

这份文档说明 `desktop-client-go/` 的实际用途、主要功能、配置项和常见问题，方便首次安装和后续联调。

## 适用场景

- Windows 桌面上常驻同步文本、文件通知和本机动作
- 想要右键菜单、全局热键、右下角提示窗的桌面场景
- 不想开托盘时，直接运行无托盘面板版

## 启动方式

```powershell
cd E:\Workspace\VSCode\cloud-clipboard\desktop-client-go
go run ./cmd/cloud-clipboard-desktop
```

如果只想开控制面板、不启用托盘：

```powershell
cd E:\Workspace\VSCode\cloud-clipboard\desktop-client-go
go run ./cmd/cloud-clipboard-panel
```

首次运行会生成 `config.json`，也可以先复制 `config.example.json` 再启动。

## 核心功能

- 托盘常驻和无托盘面板两种启动方式
- 连接 `/sync/server` 与 `/sync/ws`
- 文本剪贴板自动同步
- 右下角 Tip、系统通知、日志、关闭通知四种提示模式
- 文件通知发送、最新文本拉取、最新文件拉取到本机剪贴板
- 下载目录打开、缓存清理、保留时长控制
- 全局热键、Windows 右键子菜单、控制面板
- 右下角热角唤出提示窗，适合拖拽文件到角落后快速发送

## 右下角 Tip

Tip 模式适合文件二次确认和拖拽发送。

- `tipWidth` / `tipHeight` 控制提示窗大小
- `tipTheme` 控制浅色或深色
- `tipLeft` / `tipTop` 控制提示窗位置，`-1` 表示自动靠右下
- `tipAutoCloseSec` 控制自动关闭时间，默认 8 秒
- `tipHotCornerEnabled` 控制是否启用“拖到右下角自动唤出提示窗”

建议：

- 想减少误触就把 `tipAutoCloseSec` 调短
- 想多看一会提示就调长
- 如果不想用角落唤出，可以单独关掉 `tipHotCornerEnabled`

## 配置概览

常见配置项：

- `serverBase` 服务端地址
- `room` / `roomPassword` 房间和房间密码
- `deviceName` 设备名称
- `noticeMode` 提示模式
- `downloadDir` 下载目录
- `clipboardFileConfirmEnabled` 剪贴板文件确认
- `clipboardFileConfirmWindowSec` 确认窗口时长
- `openPanelHotkey` / `sendClipboardHotkey` / `fetchLatestHotkey` / `fetchLatestFileHotkey` / `downloadLatestHotkey`
- `shellMenuEnabled` Windows 右键菜单
- `tipWidth` / `tipHeight` / `tipTheme` / `tipLeft` / `tipTop` / `tipAutoCloseSec` / `tipHotCornerEnabled`
- `successNoticeEnabled` 成功提示

## 常见问题

### 提示窗为什么没有自动关闭？

检查 `noticeMode` 是否为 `tip`，再看 `tipAutoCloseSec` 是否被改成了很大的值。默认是 8 秒。

### 拖到右下角为什么没有弹提示窗？

确认 `noticeMode=tip` 且 `tipHotCornerEnabled=true`。如果鼠标只是快速扫过角落，可能不会触发，建议真正拖动文件过去再松手。

### 上传文件后报错怎么办？

先确认桌面端和服务端都在运行，`panelAddress` 没填错。如果是拖拽上传，尽量保持文件名正常，先用单个小文件验证。

### 面板窗口为什么没有铺满？

控制面板设计上是紧凑宽屏布局，不追求整页撑满。如果内容很多，可以在浏览器里手动拉宽窗口。

### 不想用托盘怎么办？

直接运行 `cloud-clipboard-panel`，它会只启动控制面板和同步能力，不加载托盘组件。

### Windows 右键菜单不显示？

先确认 `shellMenuEnabled=true`，再检查系统权限和安装状态。控制面板高级页里也能看到右键菜单状态。

## 相关文档

- [根 README](../README.md)
- [桌面端 README](../desktop-client-go/README.md)
- [自动同步使用与效果说明](./03-sync-usage-and-effects.md)
- [当前计划摘要 v3](./13-current-plan-summary-v3.md)
- [完成记录 v3](./14-dialogue-and-completed-summary-v3.md)
