# Cloud Clipboard 当前计划摘要（v3）

## 本轮目标

围绕桌面端联调反馈继续收口，优先处理三个直接影响可用性的点：

1. 桌面端右下角热角触发过慢、要反复晃动才弹提示
2. Windows 提示窗拖拽上传在真机上报 `GetResponse` / 连接关闭异常
3. Windows 控制面板窗口和提示窗排版偏高、偏挤，滚动条和空白感都不理想

## 当前背景

- 当前分支：`develop-codex`
- 当前重点仓库：`desktop-client-go`
- 已确认问题：
- 悬浮窗模式只有手动点按钮，没有“显示即自动点主按钮”的配置
- 页面部分 Tab 通过强制高度撑满视口，导致内容区滚动行为异常
- 提示文本较长时，容易出现“外框在滚、内容不好看也不好点”的问题
- 设备批准后，Android 端仍有一小段 `trusted=false` 的轮询空窗，需要补成事件驱动即时回灌
- 桌面端右下角 Tip 现有拖拽能力只支持“提示窗已弹出后在卡片内拖文件直接发送”，还没有“拖到右下角自动唤出提示窗”的热角触发

## 本轮执行范围

### 1. 悬浮自动确认

目标：
- 为“发送文本”和“确认下载”分别提供独立开关
- 默认关闭
- 仅自动点击主按钮
- 触发时机为悬浮卡片显示稳定后立即执行

实现口径：
- 发送悬浮窗：自动点击“发送文本”
- 接收悬浮窗：自动点击“确认下载”
- 不自动点击“打开 / 稍后 / 全部稍后 / 关闭”
- 优先直接对自身悬浮按钮执行 `performClick()`，不引入额外系统级无障碍点击复杂度

涉及文件：
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/sync/SettingsStore.kt`
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/MainActivity.kt`
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/FloatingClipboardOverlayService.kt`
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/FloatingConfirmService.kt`
- `android-sync-client/app/src/main/res/layout/activity_main.xml`
- `android-sync-client/app/src/main/res/values/strings.xml`

### 2. 主界面滚动与可达性修正

目标：
- 去掉当前“强制把非首页卡片高度锁成视口高度”的实现
- 让运行 / 权限 / 接收页内容可以完整滚到末尾
- 让长提示块、底部按钮在瘦长屏幕上可达
- 保持现有全面屏沉浸式样式，不回退成旧版普通页面

实现口径：
- 优先修正当前高度压缩逻辑
- 对长内容区改为内部可滚容器，避免外层滚动吞掉交互
- 不改动首页已确认满意的视觉结构

涉及文件：
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/MainActivity.kt`
- `android-sync-client/app/src/main/res/layout/activity_main.xml`

### 3. 设备批准即时生效

目标：
- Android 客户端收到服务端 `deviceState` 事件后，立即同步当前设备的 trusted 状态
- 避免设备在网页端已经批准后，客户端仍然要等下一轮轮询才解除 `trusted=false`

实现口径：
- 仅处理和本机 `deviceId` 匹配的 `deviceState`
- `trusted=true` 时立刻刷新本地状态并恢复发布
- `trusted=false` 时同步收回本地发布能力

涉及文件：
- `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/sync/ClipboardSyncClient.kt`

### 4. 桌面端右下角热角唤出提示

目标：
- 在 Windows 桌面端保留“拖到右下角就自动弹提示窗”的快速入口
- 仅在 `tip` 模式下启用，避免影响系统通知和日志模式
- 提升热角响应，减少“来回晃动才弹出”的体感延迟
- 保留现有提示窗内的拖放上传能力，不改服务端协议

实现口径：
- 将热角轮询从较慢间隔调到更敏感的频率
- 放宽热区边缘判定并增加短暂停留判定，降低抖动漏触发
- 提示窗出现后仍然沿用既有拖放上传链路，文件松手即可直接发送
- 提供独立开关，默认开启，方便用户关闭热角触发

涉及文件：
- `desktop-client-go/internal/app/app.go`
- `desktop-client-go/internal/app/hotcorner_windows.go`
- `desktop-client-go/internal/config/config.go`
- `desktop-client-go/internal/config/config_test.go`
- `desktop-client-go/internal/panel/server.go`
- `desktop-client-go/internal/panel/server_test.go`
- `desktop-client-go/internal/panel/static/index.html`
- `desktop-client-go/internal/app/app_test.go`

### 5. Windows 提示窗上传链路加固

目标：
- 修复拖拽文件到提示窗后上传时报连接关闭的问题
- 保留多文件拖拽上传
- 避免继续依赖脆弱的 `HttpWebRequest.GetResponse` 旧写法

实现口径：
- 改用 `System.Net.Http.HttpClient + MultipartFormDataContent`
- 每个文件都按 `files` 字段上传，和服务端解析保持一致
- 对非 2xx 返回读取响应体，便于真机直接看到失败原因
- 右下角提示窗自动关闭时长改为可配置项，默认 8 秒，允许用户按自己的拖拽习惯调整

涉及文件：
- `desktop-client-go/internal/app/windows_tip_windows.go`
- `desktop-client-go/internal/app/app_test.go`

## 本轮验证

### 自动验证

- Windows 桌面端 `go test` 通过
- Windows 桌面端 `go build .\\cmd\\cloud-clipboard-desktop` 通过
- Windows 桌面端 `go build .\\cmd\\cloud-clipboard-panel` 通过
- 热角监测和上传脚本相关单测已补充并通过
- 提示窗上传脚本已切换为 `HttpClient` 实现
- 控制面板默认窗口尺寸已压缩为更适合桌面联调的宽扁比例
- 桌面面板页面整体布局已压缩，减少滚动条出现概率

### 真机联调

- Windows 侧拖到右下角后能弹出提示窗，但触发节奏仍需要继续在真实鼠标拖拽上观察
- 文件拖入提示窗后仍有一次上传失败报错，已定位为旧脚本的连接关闭问题并开始修复
- 面板窗口和提示窗视觉密度已调紧，后续继续看瘦长屏幕上是否还会显得头重脚轻

## 本轮之后再处理

- 继续观察 Windows 热角触发在真机拖拽中的误触率和灵敏度
- 再做一次真实文件拖拽上传验收，确认 `HttpClient` 版脚本稳定
- 继续检查桌面面板在不同缩放和分辨率下是否仍有不必要滚动
- 使用真实服务端 payload 再做一次 Android 接收“下载 / 打开 / 分享 / 另存为”完整链路验收

## 文档约束

- 本轮完成后更新新的已完成文档，不覆盖旧大文档
- 文档与提交信息中不记录用户敏感信息
