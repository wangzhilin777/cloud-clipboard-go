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

## 本轮验证结果

### 构建验证

- `android-sync-client\\gradlew.bat assembleDebug` 已通过
- Debug APK 已通过 `adb install -r` 安装到真机

### 真机联调验证

- 已确认自动确认开关 `floating_auto_send_confirm_enabled` / `floating_auto_receive_confirm_enabled` 均可读取为开启状态
- 悬浮发送自动确认此前已验证可触发 `SyncService.enqueueManualPublish route=floating`
- 悬浮接收自动确认本轮已验证出现 `auto receive confirm performClick` 日志
- 悬浮接收自动确认本轮已验证进入 `confirmPayloadDownload requested` 与 `confirmPayloadDownload start` 日志
- 本轮接收调试 URL 使用不可用本地地址，仅验证动作链路是否进入下载函数，不代表真实服务端下载成功与否
- 接收页底部按钮可达性此前已通过 UI dump 验证，能看到下载、打开、分享、另存为、标记已处理、清理已处理、恢复稍后提醒等按钮

### 已知未完成

- 尚未处理“悬浮窗模式通过无障碍自动点系统确认弹窗”的更深层自动化
- 尚未继续收口 Shizuku 当前阻塞提示的准确性问题
- 尚未使用真实服务端 payload 完整验收 Android 接收侧“下载 / 打开 / 分享 / 另存为”后续动作

### 清理结果

- 已删除本轮 UI dump 临时文件
- 已清理手机端仅包含 `debug-*` 的调试 payload 缓存
- 已停止本轮测试拉起的 Android 应用进程
- 未删除手机端非测试文件或用户数据

## 本轮新增文档

- `docs/13-current-plan-summary-v3.md`
- `docs/14-dialogue-and-completed-summary-v3.md`
