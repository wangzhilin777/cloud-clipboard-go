# Windows 托盘客户端

使用方式：

1. 安装 AutoHotkey 1.1
2. 编辑 `config.ini`
3. 运行 `CloudClipboardSync.ahk`

配置项：

- `serverBase`：服务端地址，例如 `http://127.0.0.1:9501`
- `room`：房间名，可留空表示默认房间
- `roomPassword`：房间访问密码；如果该房间走全局密码，也在这里填写全局密码
- `deviceName`：当前 Windows 设备显示名
- `deviceId`：设备唯一标识，首次生成后建议保持不变

默认热键：

- `Ctrl + Alt + V`：显示/隐藏状态面板

说明：

- AHK 负责托盘、状态面板、快捷键、文本剪贴板监听
- `sync-helper.ps1` 负责连接 Go 服务端同步协议
- 当前版本支持：
  - Windows 与 Web / Android 的纯文本自动同步
  - 从托盘或状态面板选择本地文件/图片，上传后通知 Android 端确认接收
  - 未批准时仅保活并提示等待网页端批准
