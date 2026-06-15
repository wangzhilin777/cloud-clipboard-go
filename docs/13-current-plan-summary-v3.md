# Cloud Clipboard 当前计划摘要（v3）

## 本轮目标

围绕 Android 端当前真实阻塞，先完成两项直接影响可用性的收口：

1. 悬浮发送助手与悬浮接收确认增加“主按钮自动确认”能力
2. 修正主界面运行页 / 权限页 / 接收页的滚动与可达性问题

## 当前背景

- 当前分支：`develop-codex`
- 当前重点仓库：`android-sync-client`
- 已确认问题：
  - 悬浮窗模式只有手动点按钮，没有“显示即自动点主按钮”的配置
  - 页面部分 Tab 通过强制高度撑满视口，导致内容区滚动行为异常
  - 提示文本较长时，容易出现“外框在滚、内容不好看也不好点”的问题

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

## 本轮验证

### 自动验证

- Android Debug 构建通过
- 设置项保存 / 读取闭环通过
- 悬浮发送开关开 / 关逻辑检查
- 悬浮接收开关开 / 关逻辑检查
- 运行页 / 权限页 / 接收页在长内容条件下可滚到底
- 接收侧增加 debug-only 悬浮接收调试入口，用于自动化验证“弹卡片 -> 自动确认 -> 下载链路”

### 真机联调

- 悬浮发送开启自动确认后，已验证可进入手动发送链路；当服务端未信任或未连接时会停在待发送状态
- 悬浮接收开启自动确认后，已验证可自动点击主按钮并进入 `confirmPayloadDownload`
- 自动确认关闭后仍保持手动操作
- 运行页 / 权限页 / 接收页在瘦长全面屏设备上按钮可达

## 本轮之后再处理

- 悬浮窗模式“通过无障碍自动点系统确认框”这类更深一层的系统交互自动化
- Shizuku 当前模式提示与诊断口径进一步收口
- Windows 端后续联调与托盘补测
- 使用真实服务端 payload 再做一次 Android 接收“下载 / 打开 / 分享 / 另存为”完整链路验收

## 文档约束

- 本轮完成后更新新的已完成文档，不覆盖旧大文档
- 文档与提交信息中不记录用户敏感信息
