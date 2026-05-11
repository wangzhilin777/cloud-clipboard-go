#NoEnv
#SingleInstance Force
#Persistent
#InstallKeybdHook
#InstallMouseHook
SetBatchLines, -1
SetWorkingDir %A_ScriptDir%

global ConfigPath := A_ScriptDir . "\config.ini"
global RuntimeDir := A_ScriptDir
global CommandPath := RuntimeDir . "\commands.log"
global EventPath := RuntimeDir . "\events.log"
global HelperPath := A_ScriptDir . "\sync-helper.ps1"
global HelperPid := ""
global LastEventLine := 0
global ApplyingRemoteClipboard := false
global SyncPaused := false
global ClientStatus := "未启动"
global LastSyncResult := "暂无"
global PanelVisible := false
global HelperActive := false
global HelperStartAttempts := 0
global NextHelperRestartAt := 0
global RegisteredPanelHotkey := ""
global RegisteredSyncToggleHotkey := ""
global RegisteredTrayIconHotkey := ""
global HotkeyCaptureSuspended := false
global TrayIconVisible := true
global AdvancedPanelVisible := false
global StatusText := ""
global RoomText := ""
global DeviceText := ""
global PasswordText := ""
global ResultText := ""
global EditServerBase := ""
global EditRoom := ""
global EditRoomPassword := ""
global EditDeviceName := ""
global EditDeviceId := ""
global EditRuntimeDir := ""
global EditPanelHotkey := ""
global EditSyncToggleHotkey := ""
global EditTrayIconHotkey := ""
global EditAutoConnectEnabled := 0
global EditStartupEnabled := 0
global CaptureHotkeyValue := ""
global CaptureHotkeyTarget := ""

EnsureConfig()
RefreshRuntimePaths()
InitGui()
InitAdvancedGui()
InitTray()
RegisterPanelHotkey()
RegisterSyncToggleHotkey()
RegisterTrayIconHotkey()
if (ShouldShowInitialPanel())
    ShowStatusPanel()
OnClipboardChange("HandleClipboardChange")
SetTimer, PollEvents, 800
SetTimer, EnsureHelperRunning, 2000
TryAutoStartSync()
return

HandleClipboardChange(Type) {
    global ApplyingRemoteClipboard, SyncPaused, HelperActive
    if (Type != 1 || ApplyingRemoteClipboard || SyncPaused || !HelperActive)
        return
    ClipWait, 0.2
    text := Clipboard
    if (text = "")
        return
    AppendCommand("publish", text)
}

PollEvents:
    global EventPath, LastEventLine, ClientStatus, LastSyncResult, ApplyingRemoteClipboard
    if !FileExist(EventPath)
        return
    FileRead, raw, %EventPath%
    if (raw = "")
        return
    lines := StrSplit(raw, "`n", "`r")
    lineCount := lines.MaxIndex()
    while (LastEventLine < lineCount) {
        LastEventLine++
        line := Trim(lines[LastEventLine])
        if (line = "")
            continue
        parts := StrSplit(line, "|")
        type := parts[1]
        if (type = "status") {
            ClientStatus := parts[2]
            if (ClientStatus = "已连接" || ClientStatus = "已信任" || ClientStatus = "等待批准") {
                MarkConnectedOnce()
                ResetReconnectFailures()
            }
            UpdateGui()
        } else if (type = "log") {
            LastSyncResult := Base64Decode(parts[2])
            UpdateGui()
        } else if (type = "clipboard") {
            ApplyingRemoteClipboard := true
            Clipboard := Base64Decode(parts[2])
            LastSyncResult := "已接收远端文本并写入剪贴板"
            UpdateGui()
            SetTimer, ClearClipboardGuard, -1500
        }
    }
return

ClearClipboardGuard:
    global ApplyingRemoteClipboard
    ApplyingRemoteClipboard := false
return

ToggleStatusPanel:
    global PanelVisible
    if (PanelVisible)
        HideStatusPanel()
    else
        ShowStatusPanel()
return

ShowStatusPanel() {
    global PanelVisible
    UpdateGui()
    Gui, Status:Show, AutoSize, Cloud Clipboard 同步面板
    PanelVisible := true
}

HideStatusPanel() {
    global PanelVisible
    Gui, Status:Hide
    PanelVisible := false
}

StatusGuiClose:
StatusGuiEscape:
    HideStatusPanel()
return

OpenConfig:
    Run, notepad.exe "%ConfigPath%"
return

SaveConfig:
    global ConfigPath, EditServerBase, EditRoom, EditRoomPassword, EditDeviceName, EditDeviceId, EditRuntimeDir
    global EditPanelHotkey, EditSyncToggleHotkey, EditTrayIconHotkey, EditAutoConnectEnabled, EditStartupEnabled, LastSyncResult, HelperActive
    Gui, Status:Submit, NoHide
    Gui, Advanced:Submit, NoHide
    autoConnectValue := EditAutoConnectEnabled ? 1 : 0
    startupValue := EditStartupEnabled ? 1 : 0
    normalizedHotkey := NormalizeHotkey(EditPanelHotkey)
    normalizedSyncToggleHotkey := NormalizeHotkey(EditSyncToggleHotkey)
    normalizedTrayIconHotkey := NormalizeHotkey(EditTrayIconHotkey)
    normalizedRuntimeDir := NormalizeRuntimeDirForSave(EditRuntimeDir)
    IniWrite, %EditServerBase%, %ConfigPath%, sync, serverBase
    IniWrite, %EditRoom%, %ConfigPath%, sync, room
    IniWrite, %EditRoomPassword%, %ConfigPath%, sync, roomPassword
    IniWrite, % ResolveDeviceNameForSave(EditDeviceName), %ConfigPath%, sync, deviceName
    IniWrite, % ResolveDeviceIdForSave(EditDeviceId), %ConfigPath%, sync, deviceId
    IniWrite, %normalizedRuntimeDir%, %ConfigPath%, sync, runtimeDir
    IniWrite, %normalizedHotkey%, %ConfigPath%, sync, panelHotkey
    IniWrite, %normalizedSyncToggleHotkey%, %ConfigPath%, sync, syncToggleHotkey
    IniWrite, %normalizedTrayIconHotkey%, %ConfigPath%, sync, trayIconHotkey
    IniWrite, %autoConnectValue%, %ConfigPath%, sync, autoConnectEnabled
    IniWrite, %startupValue%, %ConfigPath%, sync, startupEnabled
    RefreshRuntimePaths()
    RegisterPanelHotkey()
    RegisterSyncToggleHotkey()
    RegisterTrayIconHotkey()
    SyncStartupShortcut()
    LastSyncResult := "配置已保存"
    if (HelperActive) {
        LastSyncResult := "配置已保存，并重新连接后台同步"
        RestartHelper(true)
    }
    UpdateGui()
return

BrowseRuntimeDir:
    global EditRuntimeDir
    FileSelectFolder, selectedDir, %EditRuntimeDir%, 3, 选择运行态缓存目录
    if (ErrorLevel)
        return
    GuiControl, Advanced:, EditRuntimeDir, %selectedDir%
return

OpenHotkeyCapture:
    global CaptureHotkeyValue, CaptureHotkeyTarget, EditPanelHotkey
    Gui, Advanced:Submit, NoHide
    SuspendRegisteredHotkeys()
    CaptureHotkeyTarget := "panel"
    CaptureHotkeyValue := NormalizeHotkey(EditPanelHotkey)
    Gui, HotkeyCapture:Destroy
    Gui, HotkeyCapture:New, +OwnerStatus +AlwaysOnTop +ToolWindow, 录制面板热键
    Gui, HotkeyCapture:Margin, 16, 16
    Gui, HotkeyCapture:Add, Text, w280, 按下要用于显示/隐藏面板的快捷键；也可以清空为不设置。
    Gui, HotkeyCapture:Add, Hotkey, xm y+12 w280 vCaptureHotkeyValue, %CaptureHotkeyValue%
    Gui, HotkeyCapture:Add, Button, xm y+14 w84 gSaveCapturedHotkey Default, 确认
    Gui, HotkeyCapture:Add, Button, x+8 w84 gClearCapturedHotkey, 清空
    Gui, HotkeyCapture:Add, Button, x+8 w84 gCancelCapturedHotkey, 取消
    Gui, HotkeyCapture:Show, AutoSize, 录制面板热键
return

OpenSyncToggleHotkeyCapture:
    global CaptureHotkeyValue, CaptureHotkeyTarget, EditSyncToggleHotkey
    Gui, Advanced:Submit, NoHide
    SuspendRegisteredHotkeys()
    CaptureHotkeyTarget := "syncToggle"
    CaptureHotkeyValue := NormalizeHotkey(EditSyncToggleHotkey)
    Gui, HotkeyCapture:Destroy
    Gui, HotkeyCapture:New, +OwnerStatus +AlwaysOnTop +ToolWindow, 录制同步开关热键
    Gui, HotkeyCapture:Margin, 16, 16
    Gui, HotkeyCapture:Add, Text, w280, 按下要用于一键启动/停止同步的快捷键；也可以清空为不设置。
    Gui, HotkeyCapture:Add, Hotkey, xm y+12 w280 vCaptureHotkeyValue, %CaptureHotkeyValue%
    Gui, HotkeyCapture:Add, Button, xm y+14 w84 gSaveCapturedHotkey Default, 确认
    Gui, HotkeyCapture:Add, Button, x+8 w84 gClearCapturedHotkey, 清空
    Gui, HotkeyCapture:Add, Button, x+8 w84 gCancelCapturedHotkey, 取消
    Gui, HotkeyCapture:Show, AutoSize, 录制同步开关热键
return

OpenTrayIconHotkeyCapture:
    global CaptureHotkeyValue, CaptureHotkeyTarget, EditTrayIconHotkey
    Gui, Advanced:Submit, NoHide
    SuspendRegisteredHotkeys()
    CaptureHotkeyTarget := "trayIcon"
    CaptureHotkeyValue := NormalizeHotkey(EditTrayIconHotkey)
    Gui, HotkeyCapture:Destroy
    Gui, HotkeyCapture:New, +OwnerStatus +AlwaysOnTop +ToolWindow, 录制托盘图标热键
    Gui, HotkeyCapture:Margin, 16, 16
    Gui, HotkeyCapture:Add, Text, w280, 按下要用于隐藏/恢复托盘图标的快捷键；也可以清空为不设置。
    Gui, HotkeyCapture:Add, Hotkey, xm y+12 w280 vCaptureHotkeyValue, %CaptureHotkeyValue%
    Gui, HotkeyCapture:Add, Button, xm y+14 w84 gSaveCapturedHotkey Default, 确认
    Gui, HotkeyCapture:Add, Button, x+8 w84 gClearCapturedHotkey, 清空
    Gui, HotkeyCapture:Add, Button, x+8 w84 gCancelCapturedHotkey, 取消
    Gui, HotkeyCapture:Show, AutoSize, 录制托盘图标热键
return

SaveCapturedHotkey:
    global CaptureHotkeyValue, CaptureHotkeyTarget
    Gui, HotkeyCapture:Submit, NoHide
    displayHotkey := FormatHotkeyForDisplay(CaptureHotkeyValue)
    if (CaptureHotkeyTarget = "syncToggle")
        GuiControl, Advanced:, EditSyncToggleHotkey, %displayHotkey%
    else if (CaptureHotkeyTarget = "trayIcon")
        GuiControl, Advanced:, EditTrayIconHotkey, %displayHotkey%
    else
        GuiControl, Advanced:, EditPanelHotkey, %displayHotkey%
    CloseHotkeyCapture()
return

ClearCapturedHotkey:
    global CaptureHotkeyValue
    CaptureHotkeyValue := ""
    GuiControl, HotkeyCapture:, CaptureHotkeyValue,
return

CancelCapturedHotkey:
HotkeyCaptureGuiClose:
HotkeyCaptureGuiEscape:
    CloseHotkeyCapture()
return

OpenWebConsole:
    IniRead, ServerBase, %ConfigPath%, sync, serverBase, http://127.0.0.1:9501
    ServerBase := RTrim(ServerBase, "/")
    url := ServerBase . "/#/device"
    Run, %url%
return

OpenAdvancedPanel:
    global AdvancedPanelVisible
    UpdateGui()
    Gui, Advanced:Show, AutoSize, Cloud Clipboard 高级设置
    AdvancedPanelVisible := true
return

AdvancedGuiClose:
AdvancedGuiEscape:
    global AdvancedPanelVisible
    Gui, Advanced:Hide
    AdvancedPanelVisible := false
return

SendFilesToAndroid:
    global LastSyncResult, HelperActive
    if (!HelperActive) {
        LastSyncResult := "当前未启动后台同步，请先启动同步"
        UpdateGui()
        return
    }
    paths := SelectFilesForAndroid()
    if (paths = "")
        return
    AppendCommand("payload", paths)
    LastSyncResult := "已提交文件通知任务，等待后台上传"
    UpdateGui()
return

ClearRuntimeCache:
    global CommandPath, EventPath, LastEventLine, LastSyncResult, HelperActive, ApplyingRemoteClipboard
    ApplyingRemoteClipboard := false
    LastEventLine := 0
    if (HelperActive) {
        RestartHelper(true)
        LastSyncResult := "已清理本地运行缓存，并重新连接后台同步"
    } else {
        FileDelete, %CommandPath%
        FileDelete, %EventPath%
        LastSyncResult := "已清理本地运行缓存"
    }
    UpdateGui()
    TrayTip, Cloud Clipboard, 已清理本地运行缓存, 3, 1
return

ReconnectHelper:
    if (HelperActive)
        RestartHelper(true)
    else
        StartSyncSession()
return

StartSync:
    StartSyncSession()
return

StopSync:
    StopSyncSession()
return

ToggleSyncSession:
    global HelperActive
    if (HelperActive)
        StopSyncSession()
    else
        StartSyncSession()
return

EnsureHelperRunning:
    global HelperPid, HelperActive, HelperStartAttempts, ClientStatus, LastSyncResult, NextHelperRestartAt, ConfigPath
    if (!HelperActive)
        return
    if (A_TickCount < NextHelperRestartAt)
        return
    Process, Exist, %HelperPid%
    if (HelperPid = "" || ErrorLevel = 0) {
        if (HelperStartAttempts >= 3) {
            HelperActive := false
            NextHelperRestartAt := 0
            IniWrite, stopped, %ConfigPath%, sync, lastDesiredRunningState
            ClientStatus := "重连失败"
            LastSyncResult := "连续 3 次连接失败，已停止自动重连，请检查服务端状态后手动点“启动同步”或“重新连接”"
            TrayTip, Cloud Clipboard, 连续 3 次重连失败，请检查服务端后手动重试, 5, 17
            UpdateGui()
            return
        }
        ClientStatus := "重连中"
        LastSyncResult := "后台同步连接失败，2 秒后进行第 " . (HelperStartAttempts + 1) . "/3 次连接尝试"
        NextHelperRestartAt := A_TickCount + 2000
        UpdateGui()
        StartHelper()
    }
return

TogglePause:
    global SyncPaused, HelperActive, ClientStatus, LastSyncResult
    if (!HelperActive) {
        LastSyncResult := "当前未启动后台同步，无法切换暂停状态"
        UpdateGui()
        return
    }
    SyncPaused := !SyncPaused
    status := SyncPaused ? "off" : "on"
    AppendCommand("toggle", status)
    ClientStatus := SyncPaused ? "已暂停同步" : "同步已恢复"
    LastSyncResult := SyncPaused ? "已暂停文本同步和文件通知" : "已恢复同步"
    UpdateGui()
return

ToggleTrayIconVisibility:
    global TrayIconVisible, LastSyncResult
    if (TrayIconVisible) {
        Menu, Tray, NoIcon
        TrayIconVisible := false
        LastSyncResult := "已隐藏托盘图标，可用托盘图标热键再次恢复"
    } else {
        Menu, Tray, Icon
        TrayIconVisible := true
        LastSyncResult := "已恢复托盘图标显示"
    }
    UpdateGui()
return

ToggleStartup:
    IniRead, StartupEnabled, %ConfigPath%, sync, startupEnabled, 0
    nextValue := StartupEnabled = 1 ? 0 : 1
    IniWrite, %nextValue%, %ConfigPath%, sync, startupEnabled
    SyncStartupShortcut()
    UpdateGui()
    TrayTip, Cloud Clipboard, % nextValue = 1 ? "已开启开机启动" : "已关闭开机启动", 3, 1
return

ExitClient:
    if (HelperActive)
        AppendCommand("shutdown", "")
    StopHelper()
    ExitApp
return

EnsureConfig() {
    global ConfigPath
    if FileExist(ConfigPath) {
        MigrateConfig()
        return
    }
    Random, suffix, 1000, 9999
    emptyValue := ""
    IniWrite, http://127.0.0.1:9501, %ConfigPath%, sync, serverBase
    IniWrite, %emptyValue%, %ConfigPath%, sync, room
    IniWrite, %emptyValue%, %ConfigPath%, sync, roomPassword
    IniWrite, %A_ComputerName%, %ConfigPath%, sync, deviceName
    IniWrite, %A_Now%%suffix%, %ConfigPath%, sync, deviceId
    IniWrite, %emptyValue%, %ConfigPath%, sync, runtimeDir
    IniWrite, ^!v, %ConfigPath%, sync, panelHotkey
    IniWrite, %emptyValue%, %ConfigPath%, sync, syncToggleHotkey
    IniWrite, %emptyValue%, %ConfigPath%, sync, trayIconHotkey
    IniWrite, 1, %ConfigPath%, sync, autoConnectEnabled
    IniWrite, 0, %ConfigPath%, sync, startupEnabled
    IniWrite, stopped, %ConfigPath%, sync, lastDesiredRunningState
    IniWrite, 0, %ConfigPath%, sync, hasConnectedOnce
}

MigrateConfig() {
    global ConfigPath
    missingValue := "__missing__"
    emptyValue := ""
    IniRead, DeviceName, %ConfigPath%, sync, deviceName, %missingValue%
    if (DeviceName = missingValue || DeviceName = "" || RegExMatch(DeviceName, "^Windows"))
        IniWrite, %A_ComputerName%, %ConfigPath%, sync, deviceName
    IniRead, PanelHotkey, %ConfigPath%, sync, panelHotkey, %missingValue%
    if (PanelHotkey = missingValue)
        IniWrite, ^!v, %ConfigPath%, sync, panelHotkey
    IniRead, RuntimeDirValue, %ConfigPath%, sync, runtimeDir, %missingValue%
    if (RuntimeDirValue = missingValue)
        IniWrite, %emptyValue%, %ConfigPath%, sync, runtimeDir
    IniRead, SyncToggleHotkey, %ConfigPath%, sync, syncToggleHotkey, %missingValue%
    if (SyncToggleHotkey = missingValue)
        IniWrite, %emptyValue%, %ConfigPath%, sync, syncToggleHotkey
    IniRead, TrayIconHotkey, %ConfigPath%, sync, trayIconHotkey, %missingValue%
    if (TrayIconHotkey = missingValue)
        IniWrite, %emptyValue%, %ConfigPath%, sync, trayIconHotkey
    IniRead, AutoConnectEnabled, %ConfigPath%, sync, autoConnectEnabled, %missingValue%
    if (AutoConnectEnabled = missingValue || AutoConnectEnabled = "")
        IniWrite, 1, %ConfigPath%, sync, autoConnectEnabled
    IniRead, StartupEnabled, %ConfigPath%, sync, startupEnabled, %missingValue%
    if (StartupEnabled = missingValue || StartupEnabled = "")
        IniWrite, 0, %ConfigPath%, sync, startupEnabled
    IniRead, LastDesiredRunningState, %ConfigPath%, sync, lastDesiredRunningState, %missingValue%
    if (LastDesiredRunningState = missingValue || LastDesiredRunningState = "")
        IniWrite, stopped, %ConfigPath%, sync, lastDesiredRunningState
    IniRead, HasConnectedOnce, %ConfigPath%, sync, hasConnectedOnce, %missingValue%
    if (HasConnectedOnce = missingValue || HasConnectedOnce = "")
        IniWrite, 0, %ConfigPath%, sync, hasConnectedOnce
    SyncStartupShortcut()
}

InitGui() {
    global StatusText, RoomText, DeviceText, PasswordText, ResultText
    global EditServerBase, EditRoom, EditRoomPassword, EditDeviceName
    Gui, Status:New, +ToolWindow +OwnDialogs, Cloud Clipboard 同步面板
    Gui, Status:Margin, 18, 18
    Gui, Status:Color, F6F8FC
    Gui, Status:Font, s15 Bold c1F3A5F, Segoe UI
    Gui, Status:Add, Text, xm w500, Cloud Clipboard 同步面板
    Gui, Status:Font, s9 Norm c667085, Segoe UI
    Gui, Status:Add, Text, xm y+4 w500, 托盘常驻、文本同步、安卓文件通知都集中在这里，适合单文件 exe 直接使用。
    Gui, Status:Add, Progress, xm y+12 w500 h2 Disabled cD7E2F1 BackgroundD7E2F1, 100

    Gui, Status:Font, s10 Bold c24476B, Segoe UI
    Gui, Status:Add, Text, xm y+16 w500, 连接与身份
    Gui, Status:Font, s9 Norm c667085, Segoe UI
    Gui, Status:Add, Text, xm y+2 w500, 先填服务端、房间和设备信息，再决定缓存目录和快捷键。

    Gui, Status:Font, s9 Norm c344054, Segoe UI
    Gui, Status:Add, Text, xm y+14 w92, 服务端地址
    Gui, Status:Add, Edit, x+10 yp-3 w398 h24 vEditServerBase, http://127.0.0.1:9501
    Gui, Status:Add, Text, xm y+12 w92, 房间名
    Gui, Status:Add, Edit, x+10 yp-3 w398 h24 vEditRoom,
    Gui, Status:Add, Text, xm y+12 w92, 房间密码
    Gui, Status:Add, Edit, x+10 yp-3 w398 h24 vEditRoomPassword Password,
    Gui, Status:Add, Text, xm y+12 w92, 设备名称
    Gui, Status:Add, Edit, x+10 yp-3 w398 h24 vEditDeviceName,

    Gui, Status:Add, Progress, xm y+18 w500 h1 Disabled cE1E7F0 BackgroundE1E7F0, 100
    Gui, Status:Font, s10 Bold c24476B, Segoe UI
    Gui, Status:Add, Text, xm y+12 w500, 高级设置
    Gui, Status:Font, s9 Norm c667085, Segoe UI
    Gui, Status:Add, Text, xm y+2 w500, 设备 ID、缓存目录、三个快捷键和启动规则已收进单独弹窗，默认不占主面板高度。
    Gui, Status:Add, Button, xm y+12 w120 h30 gOpenAdvancedPanel, 高级设置...

    Gui, Status:Add, Progress, xm y+18 w500 h1 Disabled cE1E7F0 BackgroundE1E7F0, 100
    Gui, Status:Font, s10 Bold c24476B, Segoe UI
    Gui, Status:Add, Text, xm y+12 w500, 当前同步状态
    Gui, Status:Font, s9 Norm c344054, Segoe UI
    Gui, Status:Add, Text, xm y+12 w500 vStatusText, 状态：未启动
    Gui, Status:Add, Text, xm y+7 w500 vRoomText, 房间：-
    Gui, Status:Add, Text, xm y+7 w500 vDeviceText, 设备：-
    Gui, Status:Add, Text, xm y+7 w500 vPasswordText, 房间密码：-
    Gui, Status:Add, Text, xm y+7 w500 vResultText, 最近结果：暂无

    Gui, Status:Add, Progress, xm y+18 w500 h1 Disabled cE1E7F0 BackgroundE1E7F0, 100
    Gui, Status:Font, s10 Bold c24476B, Segoe UI
    Gui, Status:Add, Text, xm y+12 w500, 常用操作
    Gui, Status:Font, s9 Norm c344054, Segoe UI
    Gui, Status:Add, Button, xm y+14 w94 h30 gSaveConfig Default, 保存配置
    Gui, Status:Add, Button, x+8 w94 h30 gStartSync, 启动同步
    Gui, Status:Add, Button, x+8 w94 h30 gStopSync, 停止同步
    Gui, Status:Add, Button, x+8 w94 h30 gReconnectHelper, 重新连接
    Gui, Status:Add, Button, x+8 w94 h30 gOpenWebConsole, 网页管理台
    Gui, Status:Add, Button, xm y+10 w120 h30 gOpenConfig, 打开 ini
    Gui, Status:Add, Button, x+8 w120 h30 gClearRuntimeCache, 清理本地缓存
    Gui, Status:Add, Button, x+8 w244 h34 gSendFilesToAndroid, 发送文件或图片到安卓确认接收
}

InitAdvancedGui() {
    global EditDeviceId, EditRuntimeDir, EditPanelHotkey, EditSyncToggleHotkey, EditTrayIconHotkey
    global EditAutoConnectEnabled, EditStartupEnabled
    Gui, Advanced:New, +OwnerStatus +ToolWindow +OwnDialogs, Cloud Clipboard 高级设置
    Gui, Advanced:Margin, 18, 18
    Gui, Advanced:Color, F6F8FC
    Gui, Advanced:Font, s13 Bold c1F3A5F, Segoe UI
    Gui, Advanced:Add, Text, xm w470, 高级设置
    Gui, Advanced:Font, s9 Norm c667085, Segoe UI
    Gui, Advanced:Add, Text, xm y+4 w470, 这里放不常改但很有用的配置，默认收起，后续打包单文件 exe 时也会一起保留。
    Gui, Advanced:Add, Progress, xm y+12 w470 h2 Disabled cD7E2F1 BackgroundD7E2F1, 100

    Gui, Advanced:Font, s10 Bold c24476B, Segoe UI
    Gui, Advanced:Add, Text, xm y+16 w470, 标识与缓存
    Gui, Advanced:Font, s9 Norm c344054, Segoe UI
    Gui, Advanced:Add, Text, xm y+14 w92, 设备 ID
    Gui, Advanced:Add, Edit, x+10 yp-3 w368 h24 vEditDeviceId,
    Gui, Advanced:Add, Text, xm y+12 w92, 缓存目录
    Gui, Advanced:Add, Edit, x+10 yp-3 w256 h24 vEditRuntimeDir,
    Gui, Advanced:Add, Button, x+8 yp-1 w104 h28 gBrowseRuntimeDir, 浏览目录

    Gui, Advanced:Add, Progress, xm y+18 w470 h1 Disabled cE1E7F0 BackgroundE1E7F0, 100
    Gui, Advanced:Font, s10 Bold c24476B, Segoe UI
    Gui, Advanced:Add, Text, xm y+12 w470, 快捷键
    Gui, Advanced:Font, s9 Norm c344054, Segoe UI
    Gui, Advanced:Add, Text, xm y+14 w92, 面板热键
    Gui, Advanced:Add, Edit, x+10 yp-3 w256 h24 vEditPanelHotkey,
    Gui, Advanced:Add, Button, x+8 yp-1 w104 h28 gOpenHotkeyCapture, 录制快捷键
    Gui, Advanced:Add, Text, xm y+12 w92, 同步开关键
    Gui, Advanced:Add, Edit, x+10 yp-3 w256 h24 vEditSyncToggleHotkey,
    Gui, Advanced:Add, Button, x+8 yp-1 w104 h28 gOpenSyncToggleHotkeyCapture, 录制快捷键
    Gui, Advanced:Add, Text, xm y+12 w92, 托盘图标键
    Gui, Advanced:Add, Edit, x+10 yp-3 w256 h24 vEditTrayIconHotkey,
    Gui, Advanced:Add, Button, x+8 yp-1 w104 h28 gOpenTrayIconHotkeyCapture, 录制快捷键

    Gui, Advanced:Add, Progress, xm y+18 w470 h1 Disabled cE1E7F0 BackgroundE1E7F0, 100
    Gui, Advanced:Font, s10 Bold c24476B, Segoe UI
    Gui, Advanced:Add, Text, xm y+12 w470, 启动规则
    Gui, Advanced:Font, s9 Norm c344054, Segoe UI
    Gui, Advanced:Add, CheckBox, xm y+14 w470 vEditAutoConnectEnabled, 启动客户端后按上次状态自动恢复同步
    Gui, Advanced:Add, CheckBox, xm y+8 w470 vEditStartupEnabled, 跟随 Windows 开机启动本客户端
    Gui, Advanced:Add, Button, xm y+18 w104 h30 gSaveConfig Default, 保存配置
    Gui, Advanced:Add, Button, x+8 w104 h30 gAdvancedGuiClose, 关闭
}

UpdateGui() {
    global ConfigPath, ClientStatus, LastSyncResult
    IniRead, RoomName, %ConfigPath%, sync, room,
    IniRead, DeviceName, %ConfigPath%, sync, deviceName, %A_ComputerName%
    IniRead, ServerBase, %ConfigPath%, sync, serverBase, http://127.0.0.1:9501
    IniRead, DeviceId, %ConfigPath%, sync, deviceId,
    IniRead, RuntimeDirValue, %ConfigPath%, sync, runtimeDir,
    IniRead, RoomPassword, %ConfigPath%, sync, roomPassword,
    IniRead, PanelHotkey, %ConfigPath%, sync, panelHotkey,
    IniRead, SyncToggleHotkey, %ConfigPath%, sync, syncToggleHotkey,
    IniRead, TrayIconHotkey, %ConfigPath%, sync, trayIconHotkey,
    IniRead, AutoConnectEnabled, %ConfigPath%, sync, autoConnectEnabled, 1
    IniRead, StartupEnabled, %ConfigPath%, sync, startupEnabled, 0
    masked := RoomPassword = "" ? "未设置" : "已设置"
    autoResumeText := AutoConnectEnabled = 1 ? "开启" : "关闭"
    displayHotkey := FormatHotkeyForDisplay(PanelHotkey)
    displaySyncToggleHotkey := FormatHotkeyForDisplay(SyncToggleHotkey)
    displayTrayIconHotkey := FormatHotkeyForDisplay(TrayIconHotkey)
    runtimeDirText := ResolveRuntimeDir(RuntimeDirValue)
    GuiControl, Status:, EditServerBase, %ServerBase%
    GuiControl, Status:, EditRoom, %RoomName%
    GuiControl, Status:, EditRoomPassword, %RoomPassword%
    GuiControl, Status:, EditDeviceName, %DeviceName%
    GuiControl, Advanced:, EditDeviceId, %DeviceId%
    GuiControl, Advanced:, EditRuntimeDir, %runtimeDirText%
    GuiControl, Advanced:, EditPanelHotkey, %displayHotkey%
    GuiControl, Advanced:, EditSyncToggleHotkey, %displaySyncToggleHotkey%
    GuiControl, Advanced:, EditTrayIconHotkey, %displayTrayIconHotkey%
    GuiControl, Advanced:, EditAutoConnectEnabled, % AutoConnectEnabled = 1 ? 1 : 0
    GuiControl, Advanced:, EditStartupEnabled, % StartupEnabled = 1 ? 1 : 0
    GuiControl, Status:, StatusText, 状态：%ClientStatus%
    GuiControl, Status:, RoomText, 房间：%RoomName%
    GuiControl, Status:, DeviceText, 设备：%DeviceName%（%DeviceId%） 面板热键：%displayHotkey%
    GuiControl, Status:, PasswordText, 房间密码：%masked% 自动恢复：%autoResumeText% 托盘图标键：%displayTrayIconHotkey%
    GuiControl, Status:, ResultText, 最近结果：%LastSyncResult% 同步开关键：%displaySyncToggleHotkey% 运行目录：%runtimeDirText%
}

InitTray() {
    global TrayIconVisible
    TrayIconVisible := true
    Menu, Tray, NoStandard
    Menu, Tray, Tip, Cloud Clipboard 同步客户端
    Menu, Tray, Add, 显示/隐藏同步面板, ToggleStatusPanel
    Menu, Tray, Add, 显示/隐藏托盘图标, ToggleTrayIconVisibility
    Menu, Tray, Add, 启动同步, StartSync
    Menu, Tray, Add, 停止同步, StopSync
    Menu, Tray, Add, 暂停/恢复同步, TogglePause
    Menu, Tray, Add, 重新连接, ReconnectHelper
    Menu, Tray, Add, 发送文件/图片到安卓, SendFilesToAndroid
    Menu, Tray, Add, 清理本地缓存, ClearRuntimeCache
    Menu, Tray, Add, 打开网页管理台, OpenWebConsole
    Menu, Tray, Add, 打开 ini 配置, OpenConfig
    Menu, Tray, Add, 开机启动开关, ToggleStartup
    Menu, Tray, Add, 退出, ExitClient
    Menu, Tray, Default, 显示/隐藏同步面板
}

RefreshRuntimePaths() {
    global ConfigPath, RuntimeDir, CommandPath, EventPath
    IniRead, runtimeDirValue, %ConfigPath%, sync, runtimeDir,
    RuntimeDir := ResolveRuntimeDir(runtimeDirValue)
    FileCreateDir, %RuntimeDir%
    CommandPath := RuntimeDir . "\commands.log"
    EventPath := RuntimeDir . "\events.log"
}

ResolveRuntimeDir(value) {
    value := Trim(value)
    if (value = "")
        return A_ScriptDir
    value := StrReplace(value, "/", "\")
    if RegExMatch(value, "i)^[A-Z]:\\") || SubStr(value, 1, 2) = "\\"
        return RTrim(value, "\")
    return RTrim(A_ScriptDir . "\" . value, "\")
}

NormalizeRuntimeDirForSave(value) {
    resolved := ResolveRuntimeDir(value)
    if (resolved = A_ScriptDir)
        return ""
    return resolved
}

StartHelper() {
    global HelperPid, HelperPath, ConfigPath, CommandPath, EventPath, HelperActive, HelperStartAttempts
    HelperActive := true
    HelperStartAttempts++
    Run, powershell.exe -ExecutionPolicy Bypass -File "%HelperPath%" -ConfigPath "%ConfigPath%" -CommandPath "%CommandPath%" -EventPath "%EventPath%",, Hide, HelperPid
}

StopHelper() {
    global HelperPid, HelperActive, NextHelperRestartAt
    HelperActive := false
    NextHelperRestartAt := 0
    if (HelperPid != "")
        Process, Close, %HelperPid%
    HelperPid := ""
}

RestartHelper(resetFailures := false) {
    global CommandPath, EventPath, LastEventLine
    if (resetFailures)
        ResetReconnectFailures()
    StopHelper()
    FileDelete, %CommandPath%
    FileDelete, %EventPath%
    LastEventLine := 0
    StartHelper()
}

TryAutoStartSync() {
    if (ShouldAutoResumeSync())
        StartSyncSession()
    else
        UpdateGui()
}

StartSyncSession() {
    global ConfigPath, CommandPath, EventPath, LastEventLine, HelperActive, SyncPaused, ClientStatus, LastSyncResult
    SyncPaused := false
    ResetReconnectFailures()
    IniWrite, running, %ConfigPath%, sync, lastDesiredRunningState
    if (HelperActive)
        RestartHelper(true)
    else {
        FileDelete, %CommandPath%
        FileDelete, %EventPath%
        LastEventLine := 0
        StartHelper()
    }
    ClientStatus := "连接中"
    LastSyncResult := "已按当前配置启动后台同步"
    UpdateGui()
}

StopSyncSession() {
    global ConfigPath, ClientStatus, LastSyncResult, SyncPaused
    SyncPaused := false
    ResetReconnectFailures()
    IniWrite, stopped, %ConfigPath%, sync, lastDesiredRunningState
    if (HelperActive)
        AppendCommand("shutdown", "")
    StopHelper()
    ClientStatus := "未启动"
    LastSyncResult := "已停止后台同步；下次启动前不会自动恢复"
    UpdateGui()
}

ShouldAutoResumeSync() {
    global ConfigPath
    IniRead, AutoConnectEnabled, %ConfigPath%, sync, autoConnectEnabled, 1
    IniRead, LastDesiredRunningState, %ConfigPath%, sync, lastDesiredRunningState, stopped
    IniRead, HasConnectedOnce, %ConfigPath%, sync, hasConnectedOnce, 0
    return (AutoConnectEnabled = 1 && LastDesiredRunningState = "running" && HasConnectedOnce = 1)
}

ShouldShowInitialPanel() {
    global ConfigPath
    IniRead, ServerBase, %ConfigPath%, sync, serverBase, http://127.0.0.1:9501
    IniRead, HasConnectedOnce, %ConfigPath%, sync, hasConnectedOnce, 0
    return (ServerBase = "http://127.0.0.1:9501" || HasConnectedOnce != 1)
}

RegisterPanelHotkey() {
    global RegisteredPanelHotkey, ConfigPath, LastSyncResult
    IniRead, NextHotkey, %ConfigPath%, sync, panelHotkey,
    NextHotkey := NormalizeHotkey(NextHotkey)
    if (RegisteredPanelHotkey != "")
        Hotkey, %RegisteredPanelHotkey%, ToggleStatusPanel, Off UseErrorLevel
    if (NextHotkey != "") {
        Hotkey, %NextHotkey%, ToggleStatusPanel, On UseErrorLevel
        if (ErrorLevel) {
            LastSyncResult := "面板热键无效，已忽略当前设置"
            NextHotkey := ""
        }
    }
    RegisteredPanelHotkey := NextHotkey
}

RegisterSyncToggleHotkey() {
    global RegisteredSyncToggleHotkey, ConfigPath, LastSyncResult
    IniRead, NextHotkey, %ConfigPath%, sync, syncToggleHotkey,
    NextHotkey := NormalizeHotkey(NextHotkey)
    if (RegisteredSyncToggleHotkey != "")
        Hotkey, %RegisteredSyncToggleHotkey%, ToggleSyncSession, Off UseErrorLevel
    if (NextHotkey != "") {
        Hotkey, %NextHotkey%, ToggleSyncSession, On UseErrorLevel
        if (ErrorLevel) {
            LastSyncResult := "同步开关键无效，已忽略当前设置"
            NextHotkey := ""
        }
    }
    RegisteredSyncToggleHotkey := NextHotkey
}

RegisterTrayIconHotkey() {
    global RegisteredTrayIconHotkey, ConfigPath, LastSyncResult
    IniRead, NextHotkey, %ConfigPath%, sync, trayIconHotkey,
    NextHotkey := NormalizeHotkey(NextHotkey)
    if (RegisteredTrayIconHotkey != "")
        Hotkey, %RegisteredTrayIconHotkey%, ToggleTrayIconVisibility, Off UseErrorLevel
    if (NextHotkey != "") {
        Hotkey, %NextHotkey%, ToggleTrayIconVisibility, On UseErrorLevel
        if (ErrorLevel) {
            LastSyncResult := "托盘图标热键无效，已忽略当前设置"
            NextHotkey := ""
        }
    }
    RegisteredTrayIconHotkey := NextHotkey
}

SuspendRegisteredHotkeys() {
    global RegisteredPanelHotkey, RegisteredSyncToggleHotkey, RegisteredTrayIconHotkey, HotkeyCaptureSuspended
    if (HotkeyCaptureSuspended)
        return
    if (RegisteredPanelHotkey != "")
        Hotkey, %RegisteredPanelHotkey%, ToggleStatusPanel, Off UseErrorLevel
    if (RegisteredSyncToggleHotkey != "")
        Hotkey, %RegisteredSyncToggleHotkey%, ToggleSyncSession, Off UseErrorLevel
    if (RegisteredTrayIconHotkey != "")
        Hotkey, %RegisteredTrayIconHotkey%, ToggleTrayIconVisibility, Off UseErrorLevel
    HotkeyCaptureSuspended := true
}

ResumeRegisteredHotkeys() {
    global HotkeyCaptureSuspended
    if (!HotkeyCaptureSuspended)
        return
    HotkeyCaptureSuspended := false
    RegisterPanelHotkey()
    RegisterSyncToggleHotkey()
    RegisterTrayIconHotkey()
}

CloseHotkeyCapture() {
    Gui, HotkeyCapture:Destroy
    ResumeRegisteredHotkeys()
}

NormalizeHotkey(value) {
    value := Trim(value)
    if (value = "")
        return ""
    compact := StrReplace(value, " ", "")
    if RegExMatch(value, "i)\b(ctrl|control|alt|shift|win|windows)\b") {
        normalized := RegExReplace(value, "\s*\+\s*", "+")
        parts := StrSplit(normalized, "+")
    } else {
        return compact
    }
    modifiers := ""
    key := ""
    for index, part in parts {
        part := Trim(part)
        if (part = "")
            continue
        StringLower, part, part
        if (part = "ctrl" || part = "control")
            modifiers .= "^"
        else if (part = "alt")
            modifiers .= "!"
        else if (part = "shift")
            modifiers .= "+"
        else if (part = "win" || part = "windows")
            modifiers .= "#"
        else
            key := part
    }
    key := NormalizeHotkeyKey(key)
    if (key = "")
        return ""
    return modifiers . key
}

FormatHotkeyForDisplay(value) {
    value := NormalizeHotkey(value)
    if (value = "")
        return ""
    output := ""
    loop
    {
        prefix := SubStr(value, 1, 1)
        if (prefix = "^") {
            output .= (output = "" ? "" : " + ") . "Ctrl"
            value := SubStr(value, 2)
            continue
        }
        if (prefix = "!") {
            output .= (output = "" ? "" : " + ") . "Alt"
            value := SubStr(value, 2)
            continue
        }
        if (prefix = "+") {
            output .= (output = "" ? "" : " + ") . "Shift"
            value := SubStr(value, 2)
            continue
        }
        if (prefix = "#") {
            output .= (output = "" ? "" : " + ") . "Win"
            value := SubStr(value, 2)
            continue
        }
        break
    }
    keyText := FormatHotkeyKeyForDisplay(value)
    if (keyText != "")
        output .= (output = "" ? "" : " + ") . keyText
    return output
}

NormalizeHotkeyKey(value) {
    value := Trim(value)
    if (value = "")
        return ""
    StringLower, lowerValue, value
    if (lowerValue = "semicolon")
        return ";"
    if (lowerValue = "comma")
        return ","
    if (lowerValue = "period" || lowerValue = "dot")
        return "."
    if (lowerValue = "slash")
        return "/"
    if (lowerValue = "backslash")
        return "\"
    if (lowerValue = "minus" || lowerValue = "hyphen")
        return "-"
    if (lowerValue = "quote" || lowerValue = "apostrophe")
        return "'"
    if (lowerValue = "backtick" || lowerValue = "grave")
        return "``"
    if (lowerValue = "space")
        return "Space"
    if (lowerValue = "escape")
        return "Esc"
    if (lowerValue = "tab")
        return "Tab"
    if (lowerValue = "enter")
        return "Enter"
    if (lowerValue = "esc")
        return "Esc"
    if (lowerValue = "up")
        return "Up"
    if (lowerValue = "down")
        return "Down"
    if (lowerValue = "left")
        return "Left"
    if (lowerValue = "right")
        return "Right"
    if (lowerValue = "home")
        return "Home"
    if (lowerValue = "end")
        return "End"
    if (lowerValue = "pgup")
        return "PgUp"
    if (lowerValue = "pgdn")
        return "PgDn"
    if (lowerValue = "ins")
        return "Ins"
    if (lowerValue = "del")
        return "Del"
    if (lowerValue = "bs")
        return "BS"
    if (lowerValue = "appskey")
        return "AppsKey"
    if RegExMatch(lowerValue, "^f([1-9]|1[0-9]|2[0-4])$")
        return "F" . SubStr(lowerValue, 2)
    if (StrLen(value) = 1) {
        StringUpper, value, value
        return value
    }
    return value
}

FormatHotkeyKeyForDisplay(value) {
    value := NormalizeHotkeyKey(value)
    if (value = "")
        return ""
    if (value = "Space")
        return "Space"
    if (StrLen(value) = 1) {
        StringUpper, value, value
        return value
    }
    return value
}

ResolveDeviceNameForSave(value) {
    value := Trim(value)
    if (value = "")
        return A_ComputerName
    return value
}

ResolveDeviceIdForSave(value) {
    value := Trim(value)
    if (value = "")
        return A_Now
    return value
}

SyncStartupShortcut() {
    global ConfigPath
    startupLink := A_Startup . "\CloudClipboardSync.lnk"
    IniRead, StartupEnabled, %ConfigPath%, sync, startupEnabled, 0
    if (StartupEnabled = 1) {
        FileCreateShortcut, %A_ScriptFullPath%, %startupLink%
    } else if FileExist(startupLink) {
        FileDelete, %startupLink%
    }
}

MarkConnectedOnce() {
    global ConfigPath
    IniWrite, 1, %ConfigPath%, sync, hasConnectedOnce
}

ResetReconnectFailures() {
    global HelperStartAttempts, NextHelperRestartAt
    HelperStartAttempts := 0
    NextHelperRestartAt := 0
}

AppendCommand(type, payload) {
    global CommandPath
    line := type . "|" . Base64Encode(payload) . "`n"
    FileAppend, %line%, %CommandPath%, UTF-8
}

SelectFilesForAndroid() {
    FileSelectFile, selection, M3, , 选择要通知安卓接收的文件或图片
    if (ErrorLevel)
        return ""
    lines := StrSplit(selection, "`n")
    if (lines.MaxIndex() = 1)
        return lines[1]
    dir := lines[1]
    output := ""
    Loop % lines.MaxIndex() - 1 {
        index := A_Index + 1
        fullPath := dir . "\" . lines[index]
        output .= (output = "" ? "" : "`n") . fullPath
    }
    return output
}

Base64Encode(text) {
    if (text = "")
        return ""
    chars := StrPut(text, "UTF-8")
    VarSetCapacity(bin, chars, 0)
    StrPut(text, &bin, chars, "UTF-8")
    DllCall("Crypt32.dll\CryptBinaryToString", "Ptr", &bin, "UInt", chars - 1, "UInt", 0x40000001, "Ptr", 0, "UIntP", size)
    VarSetCapacity(out, size * 2, 0)
    DllCall("Crypt32.dll\CryptBinaryToString", "Ptr", &bin, "UInt", chars - 1, "UInt", 0x40000001, "Str", out, "UIntP", size)
    return out
}

Base64Decode(text) {
    if (text = "")
        return ""
    DllCall("Crypt32.dll\CryptStringToBinary", "Str", text, "UInt", 0, "UInt", 0x1, "Ptr", 0, "UIntP", size, "Ptr", 0, "Ptr", 0)
    VarSetCapacity(bin, size, 0)
    DllCall("Crypt32.dll\CryptStringToBinary", "Str", text, "UInt", 0, "UInt", 0x1, "Ptr", &bin, "UIntP", size, "Ptr", 0, "Ptr", 0)
    return StrGet(&bin, size, "UTF-8")
}
