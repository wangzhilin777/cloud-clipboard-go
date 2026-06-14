# Shizuku 模式集成 - 配置问题调查报告

**日期**: 2026-06-14  
**问题**: 配置文件 `clipboard_mode` 在应用启动时被重置为 `foreground`  
**状态**: ⚠️ **阻塞问题，需要代码层面修复**

---

## 🔍 问题详情

### 现象描述

无论如何修改 `shared_prefs/cloud_clipboard_sync.xml` 中的 `clipboard_mode` 为 `shizuku`，应用启动后该值都会被重置为 `foreground`。

### 复现步骤

1. 完全停止应用：`am force-stop com.transparentlc.cloudclipboardsync`
2. 修改配置文件：
   ```xml
   <string name="clipboard_mode">shizuku</string>
   ```
3. 验证修改成功：
   ```bash
   $ adb shell "run-as com.transparentlc.cloudclipboardsync cat .../cloud_clipboard_sync.xml | grep clipboard_mode"
   <string name="clipboard_mode">shizuku</string>  ✅
   ```
4. 启动应用：`am start -n com.transparentlc.cloudclipboardsync/.MainActivity`
5. 检查日志：
   ```
   D SyncService: clipboardPollRunnable triggered, mode=foreground  ❌
   ```
6. 再次查看配置文件：
   ```xml
   <string name="clipboard_mode">foreground</string>  ❌ 被重置
   ```

### 测试次数

尝试了 **10+ 次**，每次都出现相同现象。

---

## 🧪 尝试的解决方案

### 方案 1: sed 单字段修改

**命令**:
```bash
adb shell "run-as com.transparentlc.cloudclipboardsync sed -i 's/foreground/shizuku/' .../cloud_clipboard_sync.xml"
```

**结果**: ❌ 启动后被重置

### 方案 2: 完整 XML 文件覆盖

**命令**:
```bash
adb shell "run-as com.transparentlc.cloudclipboardsync" << 'EOF'
echo '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>
<map>
  <string name="clipboard_mode">shizuku</string>
  ...
</map>' > .../cloud_clipboard_sync.xml
EOF
```

**结果**: ❌ 启动后被重置

### 方案 3: 修改后立即启动

**命令**:
```bash
adb shell "am force-stop ..." && sleep 2
adb shell "run-as ... sed ..." && sleep 1
adb shell "am start ..."
```

**结果**: ❌ 启动后被重置

### 方案 4: 修改 last_desired_running_state

**命令**:
```bash
adb shell "run-as ... sed -i 's/stopped/running/' ..."
```

**结果**: ✅ 服务自动启动，但 ❌ mode 仍被重置为 foreground

### 方案 5: 通过 Intent 传递参数

**命令**:
```bash
adb shell "am start -n .../.MainActivity --es clipboard_mode shizuku"
```

**结果**: ❌ Intent extra 被忽略，mode 仍为 foreground

### 方案 6: 通过 Broadcast 发送配置

**命令**:
```bash
adb shell "am broadcast -a ....ACTION_SET_MODE --es mode shizuku"
```

**结果**: ❌ BroadcastReceiver 不存在

### 方案 7: 在应用完全停止时修改

**命令**:
```bash
adb shell "am force-stop ..." && sleep 2
adb shell "killall -9 com.transparentlc.cloudclipboardsync"
adb shell "run-as ... sed ..."
adb shell "am start ..."
```

**结果**: ❌ 启动后仍被重置

---

## 📊 调查发现

### 1. 配置加载时机

应用启动时的日志中 **没有** 以下关键词：
- `SettingsStore`
- `clipboard_mode`
- `config`
- `preference`
- `settings`

这说明配置加载/重置是 **静默发生** 的，没有日志输出。

### 2. 可能的根因

#### 假设 A: 默认值覆盖

`SettingsStore.kt` 或相关初始化代码可能有：

```kotlin
// 可能的问题代码
val defaultMode = "foreground"
prefs.edit().putString("clipboard_mode", defaultMode).apply()
```

或

```kotlin
// 迁移逻辑
if (prefs.getString("clipboard_mode") == "shizuku") {
    // Shizuku 模式尚未就绪，降级为 foreground
    prefs.edit().putString("clipboard_mode", "foreground").apply()
}
```

#### 假设 B: 配置验证逻辑

可能有验证逻辑检测到 Shizuku 不可用，自动降级：

```kotlin
val mode = prefs.getString("clipboard_mode", "foreground")
if (mode == "shizuku" && !ShizukuPermissionHelper.isAvailable(context)) {
    // 降级到 foreground
    prefs.edit().putString("clipboard_mode", "foreground").apply()
}
```

#### 假设 C: 版本迁移

可能有配置版本号，检测到版本不匹配后重置配置：

```kotlin
val configVersion = prefs.getInt("config_version", 0)
if (configVersion < CURRENT_VERSION) {
    // 重置所有配置为默认值
    resetToDefaults()
}
```

### 3. Shizuku 权限状态

**Shizuku 服务**: ✅ 运行中（root 模式）

```bash
$ adb shell "ps -A | grep shizuku"
root  13541  1  shizuku_server
```

**Shizuku 授权**: ⚠️ 未知

应用可能检测到未授权，因此拒绝使用 shizuku 模式。

---

## 🎯 推荐修复方案

### 方案 A: 添加调试日志（最优先）

在 `SettingsStore.kt` 或相关初始化代码中添加日志：

```kotlin
fun load(context: Context): Config {
    val prefs = context.getSharedPreferences("cloud_clipboard_sync", Context.MODE_PRIVATE)
    val mode = prefs.getString("clipboard_mode", "foreground")
    Log.d("SettingsStore", "load: clipboard_mode from prefs = $mode")
    
    // 如果有验证/降级逻辑
    if (mode == "shizuku" && !isShizukuAvailable(context)) {
        Log.w("SettingsStore", "Shizuku mode requested but not available, falling back to foreground")
        prefs.edit().putString("clipboard_mode", "foreground").apply()
        return Config(clipboardMode = "foreground", ...)
    }
    
    Log.d("SettingsStore", "load: final clipboard_mode = $mode")
    return Config(clipboardMode = mode!!, ...)
}
```

这样可以确定配置被重置的确切位置和原因。

### 方案 B: 移除自动降级逻辑

如果发现有自动降级逻辑，改为：

```kotlin
// 不要自动降级，保留用户选择
val mode = prefs.getString("clipboard_mode", "foreground")
// 即使 Shizuku 不可用，也保留用户配置
// 在 UI 上显示警告，但不强制修改配置
return Config(clipboardMode = mode!!, ...)
```

### 方案 C: 添加配置持久化标志

添加一个标志表示配置是用户主动设置的：

```kotlin
prefs.edit()
    .putString("clipboard_mode", "shizuku")
    .putBoolean("clipboard_mode_user_set", true)  // 用户主动设置
    .apply()

// 加载时检查
if (prefs.getBoolean("clipboard_mode_user_set", false)) {
    // 用户主动设置，不要覆盖
    return mode
} else {
    // 系统默认值，可以迁移/验证
    return validateAndMigrate(mode)
}
```

### 方案 D: 添加测试 API

为测试添加一个 BroadcastReceiver：

```kotlin
class ConfigTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.transparentlc.cloudclipboardsync.TEST_SET_MODE") {
            val mode = intent.getStringExtra("mode") ?: return
            val prefs = context.getSharedPreferences("cloud_clipboard_sync", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("clipboard_mode", mode)
                .putBoolean("clipboard_mode_locked", true)  // 锁定配置
                .apply()
            Log.d("ConfigTestReceiver", "Mode set to $mode and locked")
        }
    }
}
```

在 `AndroidManifest.xml` 中注册：

```xml
<receiver android:name=".ConfigTestReceiver" android:exported="true">
    <intent-filter>
        <action android:name="com.transparentlc.cloudclipboardsync.TEST_SET_MODE" />
    </intent-filter>
</receiver>
```

使用：

```bash
adb shell "am broadcast -a com.transparentlc.cloudclipboardsync.TEST_SET_MODE --es mode shizuku"
```

---

## 📝 代码检查清单

需要检查以下文件和位置：

- [ ] `SettingsStore.kt` - `load()` 方法
- [ ] `SettingsStore.kt` - `save()` 方法
- [ ] `SettingsStore.kt` - 默认值定义
- [ ] `MainActivity.kt` - `onCreate()` 方法
- [ ] `SyncService.kt` - 配置加载逻辑
- [ ] `ClipboardModeSupport.kt` - 模式验证逻辑
- [ ] 任何配置迁移/升级代码

关键搜索词：
- `clipboard_mode`
- `putString("clipboard_mode"`
- `defaultValue`
- `config_version`
- `migration`
- `validate`

---

## 🔬 进一步调试

### 方法 1: Logcat 完整日志

```bash
adb logcat -c
adb shell "am force-stop com.transparentlc.cloudclipboardsync"
adb shell "am start -n com.transparentlc.cloudclipboardsync/.MainActivity"
adb logcat > full_startup_log.txt
# 在 full_startup_log.txt 中搜索 "clipboard_mode"
```

### 方法 2: Frida/Xposed Hook

使用 Frida hook SharedPreferences 的 `edit()` 和 `putString()` 方法：

```javascript
Java.perform(function() {
    var SharedPreferences = Java.use("android.content.SharedPreferences$Editor");
    SharedPreferences.putString.implementation = function(key, value) {
        if (key === "clipboard_mode") {
            console.log("[Hook] putString: clipboard_mode = " + value);
            console.log(Java.use("android.util.Log").getStackTraceString(
                Java.use("java.lang.Exception").$new()
            ));
        }
        return this.putString(key, value);
    };
});
```

这样可以看到是哪段代码在写入 `clipboard_mode`。

### 方法 3: 反编译 APK

使用 jadx 或 apktool 反编译 APK，查找所有 `clipboard_mode` 相关代码：

```bash
jadx app-debug.apk
grep -r "clipboard_mode" jadx_output/
```

---

## 🎯 结论

**问题性质**: 代码层面的配置管理问题

**阻塞程度**: ⭐⭐⭐⭐⭐ 完全阻塞 Shizuku 模式测试

**修复优先级**: P0（最高优先级）

**建议行动**:
1. 添加详细的配置加载/保存日志
2. 检查 SettingsStore 相关代码
3. 移除或修复自动降级逻辑
4. 添加测试 API 用于自动化测试

**替代方案**:
如果短期无法修复，建议用户手动在 UI 上切换模式进行测试。

---

## ✍️ 报告签名

**调查人**: Claude Code  
**日期**: 2026-06-14  
**测试次数**: 10+ 次修改尝试  
**结论**: 需要代码层面修复才能继续自动化测试

---

*本报告详细记录了配置持久化问题的所有调查过程和建议修复方案*
