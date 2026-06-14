# Shizuku 模式手动测试指南

**测试目标**：验证 Shizuku 模式的 Android → Windows 后台剪贴板同步功能  
**测试环境**：Android 13+、Windows 10+  
**前置条件**：Shizuku 已安装并授权

---

## 📋 测试前准备

### 1. 安装和授权

由于 MIUI 安全限制，请通过以下方式安装：

#### 方法 A：手动安装（推荐）
1. 将 APK 文件复制到手机
2. 在文件管理器中点击安装
3. 如遇安全提示，在设置中允许该来源

#### 方法 B：通过开发者选项
1. 设置 → 开发者选项 → USB 安装
2. 允许通过 USB 安装应用
3. 使用 `adb install` 命令

### 2. 配置 Shizuku

```
1. 打开 Shizuku App
2. 启动 Shizuku 服务（通过无线调试或 root）
3. 在云剪同步 App 中授予 Shizuku 权限
```

### 3. 配置服务器连接

```
服务器地址：http://192.168.31.236:9501
房间：default
设备名称：自定义
```

---

## 🧪 测试步骤

### 测试 1：Windows → Android 同步（已验证 ✅）

**预期结果**：< 1秒延迟，文本自动出现在 Android 剪贴板

**测试步骤**：
1. 在 Windows 上复制文本："test-windows-to-android"
2. 观察 Android 通知栏（应显示同步通知）
3. 在 Android 任意输入框长按粘贴
4. 验证文本是否正确

**已验证日志**：
```
06-14 14:34:01.675 D SyncService: onRemoteText text=shizuku-full-test-143403
06-14 14:34:01.675 D SyncService: applyRemoteText text=shizuku-full-test-143403
```

### 测试 2：Android → Windows 同步（待验证 ⏳）

**这是本次测试的核心目标**

**测试步骤**：

#### 前台测试
1. 打开云剪同步 App（保持在前台）
2. 在 Android 浏览器中复制文本："test-android-to-windows"
3. 切换到 Windows
4. 在任意位置粘贴
5. ✅ 预期：文本正确同步

#### 后台测试（Shizuku 模式核心功能）
1. 确认已选择 Shizuku 模式
2. 确认 Shizuku 服务运行中
3. 确认云剪同步服务已启动
4. **切换到后台**（按 Home 键或切换到其他 App）
5. 在任意 App（如浏览器、笔记）中复制文本："shizuku-background-test"
6. **不要切回云剪同步 App**
7. 在 Windows 上等待 3-5 秒
8. 在 Windows 任意位置粘贴
9. ✅ 预期：文本自动同步到 Windows

**判断标准**：
- ✅ 成功：文本在后台自动同步，无需切回 App
- ❌ 失败：需要切回 App 才能触发同步

---

## 🔍 故障排查

### 问题 1：后台复制不同步

**检查清单**：
```
□ Shizuku 服务是否运行？
  adb shell "ps -A | grep shizuku"
  
□ Shizuku 权限是否授予？
  设置 → 应用管理 → 云剪同步 → 权限
  
□ 前台服务是否运行？
  下拉通知栏查看是否有"云剪同步正在运行"
  
□ 网络连接是否正常？
  检查服务器地址和房间配置
  
□ 设备是否已批准？
  在网页端设备管理中查看设备状态
```

### 问题 2：前台同步正常，后台不同步

**这是预期行为的两种情况**：

#### 情况 A：Floating/IME模式（Android 13+ 系统限制）
- 前台：✅ 正常
- 后台：❌ 需要切回 App
- 原因：Android 13+ 隐私保护特性
- 解决：切换到 Shizuku 模式

#### 情况 B：Shizuku 模式配置问题
- 检查是否真的选择了 Shizuku 模式
- 检查 Shizuku 权限是否授予
- 重启 Shizuku 服务和云剪同步 App

---

## 📊 测试数据记录

### 测试环境信息
```
Android 设备：Redmi K50 Ultra
Android 版本：13
MIUI 版本：[填写]
Shizuku 版本：[填写]
云剪同步版本：[填写]

Windows 版本：Windows 10 Pro
服务器版本：[填写]
```

### 测试结果记录表

| 测试场景 | 模式 | 前台/后台 | 延迟 | 成功率 | 备注 |
|---------|------|-----------|------|--------|------|
| Win → Android | Shizuku | 前台 | <1s | ✅ | 已验证 |
| Win → Android | Shizuku | 后台 | <1s | ✅ | 已验证 |
| Android → Win | Shizuku | 前台 | ___ | ___ | 待测试 |
| Android → Win | Shizuku | 后台 | ___ | ___ | **核心测试** |
| Android → Win | Floating | 前台 | ___ | ___ | 对比测试 |
| Android → Win | Floating | 后台 | ___ | ___ | 对比测试 |

---

## 🎯 预期测试结果

### 成功标准

#### Shizuku 模式
- ✅ 前台同步：正常工作
- ✅ **后台同步：正常工作**（这是 Shizuku 的核心价值）
- ✅ 延迟：< 3 秒
- ✅ 成功率：> 95%

#### Floating/IME 模式（对比）
- ✅ 前台同步：正常工作
- ⚠️ 后台同步：需要切回 App（Android 13+ 限制）

---

## 🔬 技术验证点

### 代码修复验证

#### 修复 1：SettingsStore.kt
```kotlin
// 验证点：Shizuku 模式不会被自动迁移
// 操作：重启 App 后检查模式是否保持为 Shizuku
// 预期：模式保持不变
```

#### 修复 2：ShizukuClipboardReader.kt
```kotlin
// 验证点：使用 getUserPrimaryClip() 而非 getPrimaryClip()
// 操作：查看 logcat 日志
// 预期：看到 "getUserPrimaryClip" 字样
// 命令：adb logcat | grep "getUserPrimaryClip"
```

---

## 📝 测试报告模板

```markdown
## Shizuku 模式测试报告

测试人员：___________
测试日期：___________
测试环境：Android 13 / MIUI ___ / Shizuku ___

### 测试结果

#### 1. Windows → Android（前台）
- 结果：□ 成功  □ 失败
- 延迟：_______ 秒
- 备注：___________________

#### 2. Windows → Android（后台）
- 结果：□ 成功  □ 失败
- 延迟：_______ 秒
- 备注：___________________

#### 3. Android → Windows（前台）
- 结果：□ 成功  □ 失败
- 延迟：_______ 秒
- 备注：___________________

#### 4. Android → Windows（后台）⭐
- 结果：□ 成功  □ 失败
- 延迟：_______ 秒
- 是否需要切回 App：□ 是  □ 否
- 备注：___________________

### 对比测试（Floating 模式）

#### Android → Windows（后台）
- 结果：□ 成功  □ 需要切回 App
- 备注：___________________

### 结论
□ Shizuku 模式后台同步正常工作
□ Shizuku 模式优于 Floating/IME 模式
□ 发现问题：___________________
```

---

## 🚀 测试成功后的下一步

1. ✅ 更新 README 添加 Shizuku 使用说明
2. ✅ 录制演示视频
3. ✅ 发布 Release 版本
4. ✅ 推送到 GitHub

---

## 📞 支持与反馈

如遇到问题或有建议，请：
1. 查看 [故障排查](#-故障排查) 章节
2. 查看项目文档 `docs/` 目录
3. 提交 GitHub Issue

---

**重要提示**：Shizuku 模式的核心价值是**真正的后台剪贴板访问**，这是测试的重点。如果后台同步正常工作，说明我们成功解决了 Android 13+ 的剪贴板访问限制！

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
