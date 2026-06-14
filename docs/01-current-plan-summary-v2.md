# Cloud Clipboard 当前计划摘要（精简版）

## 文档目的

本文档用于快速了解项目当前状态、核心功能和待办事项。

## 项目基线

- **工作目录**：`E:\Workspace\VSCode\cloud-clipboard`
- **开发分支**：`develop-codex` (领先远端 9 个提交)
- **远端仓库**：`origin = https://github.com/wangzhilin777/cloud-clipboard-go.git`
- **基座**：`Jonnyan404/cloud-clipboard-go`

## 一期已完成功能

### 核心功能

✅ **三端纯文本自动同步**
- 网页端：浏览器剪贴板权限时自动同步，无权限时手动复制
- Windows 端：Go 桌面客户端（托盘版 + 无托盘面板版）
- Android 端：前台服务模式 + 悬浮窗模式

✅ **Android 图片/文件接收**
- 悬浮确认 + 系统通知
- 应用内下载到缓存
- 支持预览、打开、分享、另存为
- 24 小时自动清理

✅ **Android "不替换原键盘"显式发送**
- 系统分享到云剪同步
- 选中文本 → 系统处理菜单
- 主界面手动发送

✅ **服务端和协议**
- 独立同步协议（与旧 `/push` 分离）
- 设备配对批准机制
- payloadNotice 广播
- 状态持久化

### 收口工作

✅ **Android 模式体系**
- `ime` 专用输入法 → 仅作历史探索记录
- `accessibility` → 降为辅助能力
- `shizuku` → 降为诊断辅助
- 正式模式：`foreground` + `floating` + `ime_background`（原键盘后台发送）

✅ **UI 完善**
- Android 全面屏沉浸式适配
- Windows 控制面板中文化
- 错误提示优化
- 快捷键冲突检测
- Windows Tip 弹窗尺寸优化（420x170，中文长文本完整显示）

### ime_background 模式（原键盘后台发送）

利用输入法服务获取剪贴板监听权限，无需切换默认键盘：
- ✅ 在输入法管理中启用"局域网同步"输入法（不设为默认）
- ✅ ClipboardInputMethodService 注册 OnPrimaryClipChangedListener
- ✅ 监听到剪贴板变化通过 ACTION_IME_CLIPBOARD_CHANGED 通知 SyncService
- ✅ 前台复制：自动同步正常工作
- ⚠️ 后台复制限制：受 Android 13+ 系统剪贴板访问限制
- 📝 已知限制：InputMethodService 仅在被使用时运行，纯启用不足以保证持续后台监听

## 当前待办（核心）

### ✅ 已完成的高优先级任务

1. **Android floating 模式真机全场景验证** ✅
   - ✅ 前台复制：悬浮助手正常弹出
   - ✅ 点击发送：文本成功到达服务端和 Windows 端
   - ✅ Windows ↔ Android 双向同步验证通过
   - ⚠️ 后台复制限制：Android 13+ 系统禁止后台访问剪贴板（非代码 bug）

2. **Windows 客户端优化与测试** ✅
   - ✅ Tip 弹窗尺寸优化完成（420x170）
   - ✅ 房间配置修正（default 房间，与 Android 统一）
   - ✅ 文本推送接收验证通过
   - ✅ WebSocket 连接稳定性确认

3. **Android → Windows 完整同步链路验证** ✅
   - ✅ Android 手动发送 → 服务端接收
   - ✅ 服务端 WebSocket 广播
   - ✅ Windows 客户端接收并更新剪贴板
   - ✅ 日志记录完整："文本已提交到同步服务"

4. **ime_background 模式验证** ✅
   - ✅ 前台复制：自动同步正常
   - ⚠️ 后台复制：受 Android 13+ 系统限制（同 floating 模式）
   - 📝 已知限制：InputMethodService 需要被使用时才运行

### 🟡 已知限制（系统级）

**Android 13+ 剪贴板后台访问限制：**
- Android 13 引入的隐私保护特性，禁止后台应用访问剪贴板
- 影响范围：floating 和 ime_background 模式的后台复制
- 现有方案：
  1. 前台使用（正常工作）✅
  2. 切回 App 触发同步 ✅
  3. 使用 Shizuku 提权（需额外配置）
- 同类产品也受此限制（参考 APK 未使用 Shizuku）

### 🔵 低优先级（后续优化）

1. **长时间运行稳定性测试**
   - Windows 客户端托盘和快捷键功能完整性
   - 文件下载流程验证
   - 网络断线重连测试
   - 文本同步延迟测试

2. **多场景兼容性测试**

4. **Android ime_background 模式深度验证**
   - 确认输入法启用后的后台监听能力
   - 对比参考 APK 的实际行为
   - 必要时调整实现策略或文档说明

5. **文档和代码清理**
   - 移除或归档旧模式残留代码
   - 更新 README 至最新状态
   - 整理提交历史和里程碑

### 📝 低优先级

5. **文档精简**
   - 清理冗余提交记录
   - 精简已完成内容描述

## 一期不做（明确排除）

- ❌ 专用输入法方案（已探索完毕）
- ❌ Shizuku 作为后台复制主通道（系统限制）
- ❌ 无障碍作为正式同步主模式（仅保留辅助）
- ❌ 富文本同步
- ❌ 第三方输入框自动粘贴
- ❌ 图片自动写系统剪贴板

## 执行原则

- 每个里程碑形成可验证闭环
- 里程碑完成后立即提交中文 commit
- 优先真实可用，避免假装联调成功
- 不破坏原有 `/push` 和文件上传下载逻辑

## 验证环境

- **真机**：4e9e24e7 / Redmi K50 Ultra (Android 13)
- **服务端**：http://192.168.31.236:9501
- **桌面端**：Windows 10 Pro

## 最近提交（最新 10 条）

- `7e1a823` 更新对话纪要：Windows Tip 弹窗优化完成
- `e5571b5` 优化 Windows 桌面客户端 Tip 弹窗尺寸
- `72c0aea` 新增原键盘后台发送模式（ime_background）
- `fdaedd7` 修复安卓断链静默发送并补记真机联调
- `229e7ea` 补齐安卓悬浮发送助手自动弹出
- `e844945` 收口安卓旧模式诊断链路
- `50dd9a2` 收口安卓运行页旧模式摘要主语
- `3f5bc4a` 收口安卓运行时旧模式主动作分支
- `26f425f` 统一安卓原键盘发送正式文案口径
- `f6083ec` 收口安卓历史模式迁移提示口径

> 完整提交历史见 `docs/01-current-plan-summary.md`（备份）
