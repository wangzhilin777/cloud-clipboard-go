# Cloud Clipboard 已完成内容记录（v3）

## 本轮完成内容

### 1. 悬浮窗主按钮自动确认已补齐

已完成：
- 新增“悬浮发送助手显示后自动发送文本”开关
- 新增“悬浮接收卡片显示后自动确认下载”开关
- 两个开关独立保存，默认关闭
- 自动确认仅作用于主按钮，不影响打开、稍后、全部稍后、关闭等次要操作
- 接收侧新增 debug-only 调试广播，可生成测试 payload 并弹出悬浮接收卡片，便于自动化回归

实现说明：
- 悬浮发送助手显示并挂载后，若开关开启，会短延迟自动执行“发送文本”
- 悬浮接收卡片显示并挂载后，若开关开启，会短延迟自动执行“确认下载”
- 当前实现直接点击本应用悬浮按钮自身，不依赖系统级无障碍点击节点
- `ACTION_CONFIRM_PAYLOAD` 已放行，不再被当前剪贴板运行模式校验误拦截

涉及文件：
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/FloatingClipboardOverlayService.kt`
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/FloatingConfirmService.kt`
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/MainActivity.kt`
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/sync/SettingsStore.kt`
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/DebugClipboardInjectReceiver.kt`
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/sync/SyncService.kt`
- `android-sync-client/app/src/main/AndroidManifest.xml`
- `android-sync-client/app/src/main/res/layout/activity_main.xml`
- `android-sync-client/app/src/main/res/values/strings.xml`

### 2. Android 主界面滚动结构已修正

已完成：
- 去掉 `MainActivity` 中对运行页 / 权限页 / 接收页的强制视口高度锁定
- 运行页 / 权限页 / 接收页改为卡片内部 `ScrollView`
- 切换 Tab 时内容区回到顶部，避免停留在上一个 Tab 的滚动位置

修正效果：
- 不再出现“整块外框在滑、内容不好滚到底”的问题
- 长提示文字、偏下按钮、缓存区入口在瘦长屏幕上都具备可达性
- 首页结构保持不变，不动当前已确认可接受的视觉布局

### 3. 文案与摘要同步

已完成：
- 接收页增加自动确认说明文案
- 悬浮布局摘要增加自动确认当前状态
- 接收页摘要会显示发送自动确认 / 接收自动确认是否已开启

### 4. 设备批准即时回灌已补上

已完成：
- Android 客户端收到服务端 `deviceState` 事件时，会同步更新本机 `trusted` 状态
- 仅处理和当前 `deviceId` 匹配的设备事件
- 设备从 pending 被批准后，不再完全依赖 8 秒轮询才恢复推送

本轮联调观察：
- 当前真机设备最初在服务端是 pending
- 服务端重新批准后，客户端日志开始出现 `trusted=true`
- 这说明此前的“Shizuku 可用但不推送”主要是设备状态未获批，不是 Shizuku 授权失效
- 设备批准后的 trusted 回灌已经写入 v3 结论，后续不再只依赖 8 秒轮询等待状态翻转

### 5. Windows 桌面端右下角热角唤出提示已补上

已完成：
- 新增“拖到右下角自动唤出提示窗”开关，默认开启
- 仅在 Windows 桌面端 `tip` 右下角提示模式下生效
- 监测到左键拖动进入系统虚拟屏幕右下角热区时，会自动弹出现有提示窗
- 提示窗仍沿用原有拖放上传能力，文件松手后可直接发送，不改服务端协议
- 面板摘要已补上对应状态文案，方便用户知道这个开关控制的是“热角唤出”
- 本轮进一步把热角轮询调得更敏感，并加入短暂停留判定，减少“要反复晃动才弹出”的体感延迟
- 右下角提示窗自动关闭时长也已收口为可配置项，默认 8 秒，用户可以按自己的拖拽习惯调长或调短

实现说明：
- 采用轻量轮询检测鼠标左键拖动和光标位置，尽量不碰现有发送链路
- 只在 `tip` 模式下启动监测，避免影响系统通知与日志模式
- 配置、面板、状态摘要和单测已同步收口
- 当前实现还需要继续观察“真正拖拽到右下角后才弹出”的时机，用户反馈过一次提示显示过早的问题，后续会再收敛触发条件

涉及文件：
- `desktop-client-go/internal/app/app.go`
- `desktop-client-go/internal/app/hotcorner_windows.go`
- `desktop-client-go/internal/config/config.go`
- `desktop-client-go/internal/config/config_test.go`
- `desktop-client-go/internal/panel/server.go`
- `desktop-client-go/internal/panel/server_test.go`
- `desktop-client-go/internal/panel/static/index.html`
- `desktop-client-go/internal/app/app_test.go`

### 6. Windows 提示窗拖拽上传链路已加固

已完成：
- 提示窗里的拖拽上传从旧的 `HttpWebRequest.GetResponse` 写法，切换成了 `HttpClient` + `MultipartFormDataContent`
- 保留多文件上传，继续使用 `files` 字段，与服务端解析保持一致
- 对非 2xx 返回读取响应体，便于用户直接看到失败原因
- 这次修复了用户截图里看到的“基础连接已经关闭: 接收时发生错误”类异常风险

影响说明：
- 旧脚本在拖拽上传时更容易触发连接关闭异常，尤其在 Windows 真机环境里
- 新实现尽量减少 PowerShell 手写 multipart 的脆弱性，上传路径更接近标准 .NET 客户端行为

涉及文件：
- `desktop-client-go/internal/app/windows_tip_windows.go`
- `desktop-client-go/internal/app/app_test.go`

### 7. Windows 控制面板与提示窗布局压缩

已完成：
- 控制面板默认窗口尺寸改为更适合桌面联调的宽扁比例
- 桌面控制面板页面整体压缩了 hero、tab、卡片、按钮和表单间距
- 调整了移动端底部 tab 与内容边距，减少“上面没铺满、下面又太空”的感觉
- 提示窗本身也压缩了圆角、边距、按钮高度和正文占位
- 这轮主要是为了让 Windows 端信息排列更紧凑，降低滚动条出现概率，也减少视觉空白

影响说明：
- 更适合瘦长屏幕和大部分常见桌面分辨率
- 保留现有信息层级，没有把功能区打散重做

涉及文件：
- `desktop-client-go/internal/app/panel_window_windows.go`
- `desktop-client-go/internal/app/windows_tip_windows.go`
- `desktop-client-go/internal/panel/static/index.html`

## 本轮验证结果

### 构建验证

- `android-sync-client\\gradlew.bat assembleDebug` 已通过
- Debug APK 已通过 `adb install -r` 安装到真机
- `desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/panel ./internal/tray ./internal/hotkey ./internal/transfer ./internal/desktopcmd` 已通过
- `desktop-client-go` 下 `go build .\\cmd\\cloud-clipboard-desktop` 与 `go build .\\cmd\\cloud-clipboard-panel` 已通过
- 本轮新增的 `windows_tip_windows.go` 上传脚本断言也已补上并通过

### 真机联调验证

- 已确认自动确认开关 `floating_auto_send_confirm_enabled` / `floating_auto_receive_confirm_enabled` 均可读取为开启状态
- 悬浮发送自动确认此前已验证可触发 `SyncService.enqueueManualPublish route=floating`
- 悬浮接收自动确认本轮已验证出现 `auto receive confirm performClick` 日志
- 悬浮接收自动确认本轮已验证进入 `confirmPayloadDownload requested` 与 `confirmPayloadDownload start` 日志
- 本轮接收调试 URL 使用不可用本地地址，仅验证动作链路是否进入下载函数，不代表真实服务端下载成功与否
- 接收页底部按钮可达性此前已通过 UI dump 验证，能看到下载、打开、分享、另存为、标记已处理、清理已处理、恢复稍后提醒等按钮

### 已知未完成

- 尚未处理“悬浮窗模式通过无障碍自动点系统确认弹窗”的更深层自动化
- 尚未使用真实服务端 payload 完整验收 Android 接收侧“下载 / 打开 / 分享 / 另存为”后续动作
- 桌面端热角功能还需要在真机 Windows 环境里继续观察误触率、多屏坐标和右下角灵敏度
- 仍需要再做一次真实文件拖拽上传，确认 `HttpClient` 版脚本在真机上稳定可用

### 清理结果

- 已删除本轮 UI dump 临时文件
- 已清理手机端仅包含 `debug-*` 的调试 payload 缓存
- 已停止本轮测试拉起的 Android 应用进程
- 已清理桌面端编译输出的临时 EXE
- 未删除手机端非测试文件或用户数据
- 本轮 Computer Use 失败后，没有继续保留额外 Windows 侧临时进程
- 本轮新增测试后未遗留额外临时构建产物

## 本轮新增文档

- `docs/13-current-plan-summary-v3.md`
- `docs/14-dialogue-and-completed-summary-v3.md`
