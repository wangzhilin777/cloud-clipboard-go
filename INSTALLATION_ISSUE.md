# MIUI 安装限制问题说明

## 问题描述

在测试过程中遇到 MIUI 的安全限制，导致无法通过 ADB 安装应用：

```
Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]
```

## 尝试的方法

### 已尝试但失败的方法
1. ❌ `adb install` - 被 MIUI 拦截
2. ❌ `adb install -r -t -g` - 被拦截
3. ❌ `pm install` 通过 shell - 被拦截
4. ❌ Session-based 安装 - 写入成功但提交被拦截
5. ❌ `su -c pm install` - 权限被拒绝
6. ❌ Intent 打开安装界面 - 需要手动点击

### 成功的步骤
1. ✅ APK 推送到设备：`/data/local/tmp/app.apk`
2. ✅ 创建安装会话
3. ✅ 写入 APK 数据

### 失败的步骤
❌ 提交安装 - 被 MIUI 安全策略拦截

## MIUI 安全机制

MIUI 的 `USER_RESTRICTED` 策略会拦截：
- 来自 ADB 的安装请求
- 来自未知来源的应用
- 需要用户手动确认的安装

## 解决方案

### 方案 1：手动安装（推荐）
```bash
# APK 已在设备上
文件位置：/sdcard/Download/CloudClipboard.apk 或 /data/local/tmp/app.apk

步骤：
1. 打开文件管理器
2. 找到 APK 文件
3. 点击安装
4. 在 MIUI 安全提示中允许安装
```

### 方案 2：通过设置允许
```
设置 → 隐私保护 → 特殊应用权限 → 安装未知应用
→ 允许通过 ADB 或文件管理器安装
```

### 方案 3：使用 Shizuku
```bash
# 通过 Shizuku App 的应用管理功能
1. 打开 Shizuku App
2. 使用其提供的安装功能
3. 选择 APK 文件
```

### 方案 4：临时禁用 MIUI 优化
```
开发者选项 → 启用 MIUI 优化 → 关闭
（注意：可能影响系统功能）
```

## 当前状态

- ✅ 代码完全就绪
- ✅ APK 已构建
- ✅ APK 已推送到设备（`/data/local/tmp/app.apk`）
- ⏳ 需要手动安装以完成测试

## 测试步骤

一旦成功安装，请按照以下步骤测试：

1. **配置应用**
   ```
   服务器地址：http://192.168.31.236:9501
   房间：default
   模式：Shizuku
   ```

2. **授予权限**
   - Shizuku 授权
   - 通知权限
   - 无障碍（可选）

3. **测试同步**
   - 参考 `MANUAL_TEST_GUIDE.md`
   - 重点测试 Android → Windows 后台同步

## 技术说明

这是 MIUI 的**正常安全特性**，不是代码问题：
- 从 MIUI 12 开始引入
- 保护用户免受恶意应用安装
- 需要用户显式确认安装

其他 ROM（如原生 Android、一加等）可能不会遇到此问题。

## 参考

- [MIUI 安全指南](https://www.mi.com/global/service/userguide/)
- 类似问题讨论：[XDA Forums](https://forum.xda-developers.com/)

---

**结论**：APK 已准备就绪并推送到设备，需要手动安装以继续测试。

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
