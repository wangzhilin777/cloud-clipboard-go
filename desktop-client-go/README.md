# Cloud Clipboard Desktop Go Client

这是新的电脑端 Go 客户端起点，目标是逐步替代当前 `windows-client` 下的 AHK 方案。

当前这版先完成最小闭环：

- 读取本地 `config.json`
- 连接 Go 服务端 `/api/sync/server` 与 `/sync/ws`
- 走现有 `roomPassword + 配对批准` 模型
- 纯文本剪贴板轮询同步
- 收到远端文本后写回系统剪贴板
- 未获批设备保持连接但不发送文本
- 本地 `state.json` 记录连接状态与最近一次远端内容
- 支持桌面通知模式：`popup / log / off`

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
