# Cloud Clipboard Desktop Go Client

这是新的电脑端 Go 客户端起点，目标是逐步替代当前 `windows-client` 下的 AHK 方案。

当前这版先完成最小闭环：

- 读取本地 `config.json`
- 默认以托盘模式启动，左键可直接打开面板，右键菜单可查看状态、立即重连、退出
- 连接 Go 服务端 `/api/sync/server` 与 `/sync/ws`
- 走现有 `roomPassword + 配对批准` 模型
- 纯文本剪贴板轮询同步
- 收到远端文本后写回系统剪贴板
- 未获批设备保持连接但不发送文本
- 本地 `state.json` 记录连接状态与最近一次远端内容
- 支持桌面通知模式：`popup / log / off`
- 内置本地控制面板，可查看状态、修改配置、保存后触发重连
- 支持失败自动重连次数与重连间隔控制，超过上限后停止自动重连
- 支持启动时自动拉起控制面板，也可从面板再次调用系统浏览器打开
- 支持本机选择文件上传，并向同步房间广播文件接收通知
- 支持手动发送输入文本或当前剪贴板文本到同步房间
- 支持主动拉取服务端最新文本到本机剪贴板，或下载最新文件到本地目录

后续会继续补：

- 托盘
- 小弹窗 / tip / 系统通知
- 文件通知发送与接收
- Windows 右键菜单
- Linux / macOS 平台差异适配

## 运行

```powershell
cd E:\Workspace\VSCode\cloud-clipboard\desktop-client-go
C:\Program Files\Go\bin\go.exe run ./cmd/cloud-clipboard-desktop
```

首次运行会自动生成 `config.json`。

如果只想无托盘运行，方便调试或自动化验证：

```powershell
cd E:\Workspace\VSCode\cloud-clipboard\desktop-client-go
C:\Program Files\Go\bin\go.exe run ./cmd/cloud-clipboard-desktop -headless
```

默认会同时启动本地控制面板：

- 地址默认是 `http://127.0.0.1:9530/`
- 可在面板内修改 `serverBase / room / roomPassword / deviceName / pollInterval / noticeMode`
- 可在面板内修改失败自动重连次数与重连间隔
- 可配置启动时是否自动打开面板，也可点击按钮调用系统浏览器打开
- 可从面板按钮或托盘菜单直接选择文件并发送到同步房间
- 可从面板或托盘直接手动发送当前剪贴板文本
- 可配置下载目录，并从面板或托盘主动拉取最新文本/文件
- 保存后会立即触发同步客户端重连
- 如果修改了 `panelAddress`，需要用新地址重新打开页面
