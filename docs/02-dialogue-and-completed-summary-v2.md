# Cloud Clipboard 对话纪要与已完成内容（精简版）

## 文档目的

本文档记录项目开发过程中的关键决策、用户偏好和已完成内容。

## 用户偏好

- ✅ 中文输出
- ✅ 优先直接动手，不要长篇空谈
- ✅ 每到可验证里程碑就提交中文 commit
- ✅ 保留旧 `/push`、旧网页文件上传下载逻辑
- ✅ 给出实际验证结果，不假装联调

## 项目迁移脉络

1. **Node 基座** → 完成一期同步能力
2. **Go 基座** → `cloud-clipboard-go`，双层密码模型
3. **Windows 端** → AHK 方案 → Go 桌面客户端
4. **当前工作仓库**：`E:\Workspace\VSCode\cloud-clipboard`

## 已完成的核心能力

### 服务端
- ✅ 独立同步协议（与旧 `/push` 分离）
- ✅ 设备配对批准、trusted 状态持久化
- ✅ `payloadNotice` 广播
- ✅ Recent 历史管理和状态清理

### 网页端
- ✅ 纯文本自动同步闭环
- ✅ 权限不足时降级显示 + 手动复制
- ✅ 时间流记录合并与即时刷新
- ✅ 作为管理端批准设备

### Windows 桌面端
- ✅ Go 跨平台桌面客户端（替代 AHK）
- ✅ 托盘版 + 无托盘面板版
- ✅ 控制面板中文化
- ✅ 文本同步、文件发送
- ✅ 右键菜单、快捷键
- ✅ 缓存管理

### Android 同步客户端
- ✅ 文本同步闭环
- ✅ 图片/文件通知确认接收
- ✅ 下载到应用缓存（24 小时自动清理）
- ✅ 接收页：预览、打开、分享、另存、已处理
- ✅ 全面屏沉浸式适配
- ✅ "不替换原键盘"显式发送（系统分享/选中文本/手动发送）
- ✅ floating 模式（复制后自动弹出悬浮助手）

## 关键技术决策

### Android 后台复制方案演进

**探索过的方案**：
- ❌ `ime` 专用输入法 → 需要切换默认输入法，用户体验不佳
- ❌ `shizuku` 作为主通道 → 系统限制无法绕过后台剪贴板访问
- ⚠️ `accessibility` 无障碍 → 降为辅助能力，不作为主模式

**当前正式方案**：
- ✅ `foreground` 前台服务模式（最稳定）
- ✅ `floating` 悬浮窗模式（复制后快速发送）
- ✅ "不替换原键盘"显式发送（系统分享/选中文本/手动发送）

### Windows 桌面端演进

- ❌ AHK 方案 → 可维护性差，bug 多
- ✅ Go 桌面客户端 → 跨平台，可维护性好
- ✅ 托盘实现替换 → 修复右键菜单稳定性问题

## 重要修复记录

### Android 端
- ✅ 修复断链静默发送问题
- ✅ 修复剪贴板重复回传与同步回环
- ✅ 修复后台复制快照兜底（Chrome 占位词过滤）
- ✅ 修复接收页按钮状态（缓存文件存在判断）
- ✅ 修复 loopback 地址兼容（127.0.0.1 改写）
- ✅ 优化无障碍启用状态判断（系统服务枚举兜底）

### Windows 端
- ✅ 修复桌面端配置 BOM 兼容
- ✅ 修复控制面板文件发送（multipart/form-data）
- ✅ 修复托盘右键菜单（WM_CONTEXTMENU 支持）
- ✅ 修复右键菜单注册状态诊断
- ✅ 优化通知模式默认兜底
- ✅ 优化快捷键配置校验

### 服务端
- ✅ 补充同步状态清理测试
- ✅ 补充同步广播隔离测试
- ✅ 修复同步 recent 历史数量配置

## 2026-06-13 进展

### 代码审查完成
- ✅ **floating 模式代码审查**：确认自动弹出逻辑正确（SyncService.kt:363-372）
- ✅ **悬浮窗实现审查**：确认功能完整（预览、发送、拖动、倒计时）
- ✅ **防重复机制审查**：确认防回环、防重复逻辑完善
- ✅ **旧模式清理检查**：确认主界面只保留 foreground 和 floating 两个模式
- ✅ **自动迁移逻辑检查**：确认旧模式会自动迁移到 foreground

### 文档整理完成
- ✅ 创建精简版计划摘要：`docs/01-current-plan-summary-v2.md`
- ✅ 创建精简版对话纪要：`docs/02-dialogue-and-completed-summary-v2.md`
- ✅ 创建核心待办清单：`docs/04-current-todos.md`
- ✅ 创建 floating 模式验证报告：`docs/05-floating-mode-verification.md`

### 真机配置验证
- ✅ 确认真机在线：4e9e24e7
- ✅ 确认配置正确：`clipboard_mode=floating`, `floating_enabled=true`
- ✅ 确认服务端地址：`http://192.168.31.236:9501`
- ✅ 确认同步服务运行中（前台服务已启动 2h51m+）

### 快捷键和提示检查
- ✅ **Windows 快捷键**：已实现 5 个快捷键（打开面板、发送剪贴板、拉取文本、拉取文件、下载文件）
- ✅ **控制面板提示**：已有完善的中文提示和帮助文本
- ✅ **Android 提示文案**：已有完整的悬浮助手、模式说明、运行时提示

## 2026-06-14 进展

### Windows 桌面客户端验证
- ✅ **服务端网页前端修复**：启用静态文件服务（`-static` 参数），http://127.0.0.1:9501 可正常访问
- ✅ **桌面客户端连接验证**：设备批准流程正常，`connected: true`, `trusted: true`
- ✅ **Windows → 服务端同步**：成功（测试文本 "windows-desktop-sync-233756"）
- ✅ **桌面客户端控制面板**：http://127.0.0.1:9530 正常工作

### Windows Tip 弹窗优化
- ✅ **问题发现**：默认 348x140 尺寸导致中文长文件名显示不全（"...确认发送。"被截断）
- ✅ **尺寸优化**：增大默认宽度 348→420px，高度 140→170px
- ✅ **效果验证**：文件名"02-dialogue-and-completed-summary-v2.md，可在 8 秒内确认发送。"完整显示
- ✅ **代码修改**：`windows_tip_windows.go` 第26-30行默认值更新
- ✅ **提交记录**：e5571b5 "优化 Windows 桌面客户端 Tip 弹窗尺寸"

### ime_background 模式深度分析
- ✅ **问题发现**：后台复制不会自动上传，切回App前台才上传
- ✅ **根本原因确认**：Android 10+ 限制后台应用读取剪贴板，`clipboardManager.primaryClip` 在后台返回 null
- ✅ **参考APK分析**：通过反编译和截图分析，确认参考APK使用"启用输入法"方式获得剪贴板权限
- ⚠️ **InputMethodService 生命周期限制**：
  - 启用输入法 ≠ 输入法服务运行
  - `onCreate()` 只在用户切换到该输入法时调用
  - 无法通过 InputMethodService 实现真正的后台监听

### ime_background 实现尝试
- ✅ **添加剪贴板监听器**：在 ClipboardInputMethodService 中添加 OnPrimaryClipChangedListener
- ✅ **添加 SyncService Action**：新增 ACTION_IME_CLIPBOARD_CHANGED 和 notifyImeClipboardChanged()
- ✅ **编译安装成功**：真机已安装新版本
- ❌ **验证失败**：监听器未触发，因为 InputMethodService 未运行（未切换到该输入法）

### Windows Tip 弹窗优化
- ✅ **问题发现**：默认 348x140 尺寸导致中文长文件名显示不全（"...确认发送。"被截断）
- ✅ **尺寸优化**：增大默认宽度 348→420px，高度 140→170px
- ✅ **效果验证**：文件名"02-dialogue-and-completed-summary-v2.md，可在 8 秒内确认发送。"完整显示
- ✅ **代码修改**：`windows_tip_windows.go` 第26-30行默认值更新
- ✅ **提交记录**：e5571b5 "优化 Windows 桌面客户端 Tip 弹窗尺寸"

### Android floating 模式测试准备
- ✅ **运行模式确认**：真机当前运行 floating（原键盘悬浮发送）模式
- ✅ **服务状态确认**：SyncService 前台服务运行正常（isForeground=true）
- ✅ **无障碍服务启用**：已通过 adb 启用云剪同步的无障碍服务
- ✅ **悬浮窗权限确认**：SYSTEM_ALERT_WINDOW 已授权
- ✅ **设备房间配置**：Android 和 Windows 都配置为 "default" 房间

### 同步链路验证
- ✅ **Android → 服务端**：手动发送测试成功（text: "rry"，messageId: e9135b93-...）
- ✅ **Windows → 服务端**：自动同步测试成功（text: "android-windows-sync-test-032326"）
- ✅ **服务端消息存储**：bootstrap API 可正常查询 recentMessages（共51条历史消息）
- ⚠️ **Windows → Android**：消息已到达服务端，但 Android 端未观察到接收日志
- ⚠️ **floating 自动触发**：UI 自动化复制操作复杂，需要更多调试时间或人工辅助验证

## 当前待继续推进

### 🔴 高优先级任务

1. **ime_background 模式改进** ⚠️ 技术探索中
   - 问题：InputMethodService 生命周期限制导致监听器不工作
   - 待尝试：在 SyncService 中直接利用输入法权限读取剪贴板
   - 或考虑：改用无障碍 + 输入法混合方案

2. **floating 模式真机端到端验证** ⚠️ 待自动化测试
   - 代码审查已通过
   - 需要自动化复制文本观察悬浮窗是否弹出
   - 需要验证点击发送后文本到达服务端

3. **真实场景覆盖测试** ⚠️ 待自动化测试
   - Chrome、QQ、微信、笔记类 App
   - 验证不会误发普通打字
   - 验证不影响文件接收悬浮卡片

### 🔧 中优先级任务

4. **Windows 客户端优化** ⚠️ 进行中
   - 托盘通知弹窗大小和位置优化（自行截图查看）
   - 快捷键功能验证
   - 长时间稳定性测试

5. **旧模式清理** ✅ 主界面已完成
   - ✅ 主界面只保留 foreground 和 floating
   - ✅ 自动迁移逻辑已实现
   - 📝 README 旧说明清理（待下一步）

### 📝 低优先级

6. **文档精简**
   - ✅ 已完成精简版文档创建
   - 📝 清理冗余提交记录描述

## 验证环境

- **真机**：4e9e24e7 / Redmi K50 Ultra (Android 13)
- **服务端**：http://192.168.31.236:9501
- **桌面端**：Windows 10 Pro
- **开发分支**：`develop-codex` (领先远端 9 个提交)

## Android 真机联调注意事项

1. **无障碍授权恢复**：如安装后丢失，使用爱玩机工具箱 → 搜索"无障碍" → 无障碍助手恢复
2. **Gradle 构建**：串行执行 `testDebugUnitTest` 和 `assembleDebug`，避免 Windows 文件锁
3. **真机可覆盖安装**：有 root 环境，确有必要时可安装
4. **避免删除非调试数据**：只删除 `codex-*` 测试数据

> 详细历史记录见 `docs/02-dialogue-and-completed-summary.md`（备份）
