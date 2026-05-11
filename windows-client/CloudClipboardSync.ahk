#NoEnv
#SingleInstance Force
#Persistent
#InstallKeybdHook
#InstallMouseHook
SetBatchLines, -1
SetWorkingDir %A_ScriptDir%

global ConfigPath := A_ScriptDir . "\config.ini"
global CommandPath := A_ScriptDir . "\commands.log"
global EventPath := A_ScriptDir . "\events.log"
global HelperPath := A_ScriptDir . "\sync-helper.ps1"
global HelperPid := ""
global LastEventLine := 0
global ApplyingRemoteClipboard := false
global SyncPaused := false
global ClientStatus := "未启动"
global LastSyncResult := "暂无"
global PanelVisible := false
global HelperActive := false
global HelperRestartFailures := 0
global RegisteredPanelHotkey := ""
global RegisteredSyncToggleHotkey := ""
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
global EditPanelHotkey := ""
global EditSyncToggleHotkey := ""
global EditAutoConnectEnabled := 0
global EditStartupEnabled := 0
global CaptureHotkeyValue := ""
global CaptureHotkeyTarget := ""

EnsureConfig()
InitGui()
InitTray()
RegisterPanelHotkey()
RegisterSyncToggleHotkey()
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
    global ConfigPath, EditServerBase, EditRoom, EditRoomPassword, EditDeviceName, EditDeviceId
    global EditPanelHotkey, EditSyncToggleHotkey, EditAutoConnectEnabled, EditStartupEnabled, LastSyncResult, HelperActive
    Gui, Status:Submit, NoHide
    autoConnectValue := EditAutoConnectEnabled ? 1 : 0
    startupValue := EditStartupEnabled ? 1 : 0
    normalizedHotkey := NormalizeHotkey(EditPanelHotkey)
    normalizedSyncToggleHotkey := NormalizeHotkey(EditSyncToggleHotkey)
    IniWrite, %EditServerBase%, %ConfigPath%, sync, serverBase
    IniWrite, %EditRoom%, %ConfigPath%, sync, room
    IniWrite, %EditRoomPassword%, %ConfigPath%, sync, roomPassword
    IniWrite, % ResolveDeviceNameForSave(EditDeviceName), %ConfigPath%, sync, deviceName
    IniWrite, % ResolveDeviceIdForSave(EditDeviceId), %ConfigPath%, sync, deviceId
    IniWrite, %normalizedHotkey%, %ConfigPath%, sync, panelHotkey
    IniWrite, %normalizedSyncToggleHotkey%, %ConfigPath%, sync, syncToggleHotkey
    IniWrite, %autoConnectValue%, %ConfigPath%, sync, autoConnectEnabled
    IniWrite, %startupValue%, %ConfigPath%, sync, startupEnabled
    RegisterPanelHotkey()
    RegisterSyncToggleHotkey()
    SyncStartupShortcut()
    LastSyncResult := "配置已保存"
    if (HelperActive) {
        LastSyncResult := "配置已保存，并重新连接后台同步"
        RestartHelper(true)
    }
    UpdateGui()
return

OpenHotkeyCapture:
    global CaptureHotkeyValue, CaptureHotkeyTarget, EditPanelHotkey
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

SaveCapturedHotkey:
    global CaptureHotkeyValue, CaptureHotkeyTarget
    Gui, HotkeyCapture:Submit, NoHide
    displayHotkey := FormatHotkeyForDisplay(CaptureHotkeyValue)
    if (CaptureHotkeyTarget = "syncToggle")
        GuiControl, Status:, EditSyncToggleHotkey, %displayHotkey%
    else
        GuiControl, Status:, EditPanelHotkey, %displayHotkey%
    Gui, HotkeyCapture:Destroy
return

ClearCapturedHotkey:
    global CaptureHotkeyValue
    CaptureHotkeyValue := ""
    GuiControl, HotkeyCapture:, CaptureHotkeyValue,
return

CancelCapturedHotkey:
HotkeyCaptureGuiClose:
HotkeyCaptureGuiEscape:
    Gui, HotkeyCapture:Destroy
return

OpenWebConsole:
    IniRead, ServerBase, %ConfigPath%, sync, serverBase, http://127.0.0.1:9501
    ServerBase := RTrim(ServerBase, "/")
    url := ServerBase . "/#/device"
    Run, %url%
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
    global HelperPid, HelperActive, HelperRestartFailures, ClientStatus, LastSyncResult
    if (!HelperActive)
        return
    Process, Exist, %HelperPid%
    if (HelperPid = "" || ErrorLevel = 0) {
        HelperRestartFailures++
        if (HelperRestartFailures >= 3) {
            HelperActive := false
            ClientStatus := "重连失败"
            LastSyncResult := "连续 3 次重连失败，请检查服务端状态后手动点“启动同步”或“重新连接”"
            TrayTip, Cloud Clipboard, 连续 3 次重连失败，请检查服务端后手动重试, 5, 17
            UpdateGui()
            return
        }
        ClientStatus := "重连中"
        LastSyncResult := "后台同步连接失败，正在进行第 " . HelperRestartFailures . "/3 次重试"
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
    IniWrite, ^!v, %ConfigPath%, sync, panelHotkey
    IniWrite, %emptyValue%, %ConfigPath%, sync, syncToggleHotkey
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
    IniRead, SyncToggleHotkey, %ConfigPath%, sync, syncToggleHotkey, %missingValue%
    if (SyncToggleHotkey = missingValue)
        IniWrite, %emptyValue%, %ConfigPath%, sync, syncToggleHotkey
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
    global EditServerBase, EditRoom, EditRoomPassword, EditDeviceName, EditDeviceId
    global EditPanelHotkey, EditAutoConnectEnabled, EditStartupEnabled
    Gui, Status:New, +AlwaysOnTop +ToolWindow, Cloud Clipboard 同步面板
    Gui, Status:Margin, 16, 16
    Gui, Status:Add, GroupBox, w460 h326, 同步配置
    Gui, Status:Add, Text, xm+14 yp+28 w90, 服务端地址
    Gui, Status:Add, Edit, x+8 yp-3 w330 vEditServerBase, http://127.0.0.1:9501
    Gui, Status:Add, Text, xm+14 y+14 w90, 房间名
    Gui, Status:Add, Edit, x+8 yp-3 w330 vEditRoom,
    Gui, Status:Add, Text, xm+14 y+14 w90, 房间密码
    Gui, Status:Add, Edit, x+8 yp-3 w330 vEditRoomPassword Password,
    Gui, Status:Add, Text, xm+14 y+14 w90, 设备名称
    Gui, Status:Add, Edit, x+8 yp-3 w330 vEditDeviceName,
    Gui, Status:Add, Text, xm+14 y+14 w90, 设备 ID
    Gui, Status:Add, Edit, x+8 yp-3 w330 vEditDeviceId,
    Gui, Status:Add, Text, xm+14 y+14 w90, 面板热键
    Gui, Status:Add, Edit, x+8 yp-3 w220 vEditPanelHotkey,
    Gui, Status:Add, Button, x+8 yp-1 w102 h28 gOpenHotkeyCapture, 录制快捷键
    Gui, Status:Add, Text, xm+14 y+14 w90, 同步开关键
    Gui, Status:Add, Edit, x+8 yp-3 w220 vEditSyncToggleHotkey,
    Gui, Status:Add, Button, x+8 yp-1 w102 h28 gOpenSyncToggleHotkeyCapture, 录制快捷键
    Gui, Status:Add, CheckBox, xm+14 y+16 vEditAutoConnectEnabled, 启动客户端后按上次状态自动恢复同步
    Gui, Status:Add, CheckBox, xm+14 y+8 vEditStartupEnabled, 跟随 Windows 开机启动本客户端

    Gui, Status:Add, GroupBox, xm y+22 w460 h132, 同步状态
    Gui, Status:Add, Text, xm+14 yp+28 w432 vStatusText, 状态：未启动
    Gui, Status:Add, Text, xm+14 y+10 w432 vRoomText, 房间：-
    Gui, Status:Add, Text, xm+14 y+10 w432 vDeviceText, 设备：-
    Gui, Status:Add, Text, xm+14 y+10 w432 vPasswordText, 房间密码：-
    Gui, Status:Add, Text, xm+14 y+10 w432 vResultText, 最近结果：暂无

    Gui, Status:Add, Button, xm y+18 w84 gSaveConfig Default, 保存配置
    Gui, Status:Add, Button, x+8 w84 gStartSync, 启动同步
    Gui, Status:Add, Button, x+8 w84 gStopSync, 停止同步
    Gui, Status:Add, Button, x+8 w84 gReconnectHelper, 重新连接
    Gui, Status:Add, Button, x+8 w84 gOpenWebConsole, 网页管理台
    Gui, Status:Add, Button, xm y+10 w112 gOpenConfig, 打开 ini
    Gui, Status:Add, Button, x+8 w340 h32 gSendFilesToAndroid, 发送文件或图片到安卓确认接收
}

UpdateGui() {
    global ConfigPath, ClientStatus, LastSyncResult
    IniRead, RoomName, %ConfigPath%, sync, room,
    IniRead, DeviceName, %ConfigPath%, sync, deviceName, %A_ComputerName%
    IniRead, ServerBase, %ConfigPath%, sync, serverBase, http://127.0.0.1:9501
    IniRead, DeviceId, %ConfigPath%, sync, deviceId,
    IniRead, RoomPassword, %ConfigPath%, sync, roomPassword,
    IniRead, PanelHotkey, %ConfigPath%, sync, panelHotkey,
    IniRead, SyncToggleHotkey, %ConfigPath%, sync, syncToggleHotkey,
    IniRead, AutoConnectEnabled, %ConfigPath%, sync, autoConnectEnabled, 1
    IniRead, StartupEnabled, %ConfigPath%, sync, startupEnabled, 0
    masked := RoomPassword = "" ? "未设置" : "已设置"
    autoResumeText := AutoConnectEnabled = 1 ? "开启" : "关闭"
    displayHotkey := FormatHotkeyForDisplay(PanelHotkey)
    displaySyncToggleHotkey := FormatHotkeyForDisplay(SyncToggleHotkey)
    GuiControl, Status:, EditServerBase, %ServerBase%
    GuiControl, Status:, EditRoom, %RoomName%
    GuiControl, Status:, EditRoomPassword, %RoomPassword%
    GuiControl, Status:, EditDeviceName, %DeviceName%
    GuiControl, Status:, EditDeviceId, %DeviceId%
    GuiControl, Status:, EditPanelHotkey, %displayHotkey%
    GuiControl, Status:, EditSyncToggleHotkey, %displaySyncToggleHotkey%
    GuiControl, Status:, EditAutoConnectEnabled, % AutoConnectEnabled = 1 ? 1 : 0
    GuiControl, Status:, EditStartupEnabled, % StartupEnabled = 1 ? 1 : 0
    GuiControl, Status:, StatusText, 状态：%ClientStatus%
    GuiControl, Status:, RoomText, 房间：%RoomName%
    GuiControl, Status:, DeviceText, 设备：%DeviceName%（%DeviceId%） 面板热键：%displayHotkey%
    GuiControl, Status:, PasswordText, 房间密码：%masked% 自动恢复：%autoResumeText%
    GuiControl, Status:, ResultText, 最近结果：%LastSyncResult% 同步开关键：%displaySyncToggleHotkey%
}

InitTray() {
    Menu, Tray, NoStandard
    Menu, Tray, Tip, Cloud Clipboard 同步客户端
    Menu, Tray, Add, 显示/隐藏同步面板, ToggleStatusPanel
    Menu, Tray, Add, 启动同步, StartSync
    Menu, Tray, Add, 停止同步, StopSync
    Menu, Tray, Add, 暂停/恢复同步, TogglePause
    Menu, Tray, Add, 重新连接, ReconnectHelper
    Menu, Tray, Add, 发送文件/图片到安卓, SendFilesToAndroid
    Menu, Tray, Add, 打开网页管理台, OpenWebConsole
    Menu, Tray, Add, 打开 ini 配置, OpenConfig
    Menu, Tray, Add, 开机启动开关, ToggleStartup
    Menu, Tray, Add, 退出, ExitClient
    Menu, Tray, Default, 显示/隐藏同步面板
}

StartHelper() {
    global HelperPid, HelperPath, ConfigPath, CommandPath, EventPath, HelperActive
    HelperActive := true
    Run, %ComSpec% /c powershell -ExecutionPolicy Bypass -File "%HelperPath%" -ConfigPath "%ConfigPath%" -CommandPath "%CommandPath%" -EventPath "%EventPath%",, Hide, HelperPid
}

StopHelper() {
    global HelperPid, HelperActive
    HelperActive := false
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
    global RegisteredPanelHotkey, ConfigPath
    IniRead, NextHotkey, %ConfigPath%, sync, panelHotkey,
    NextHotkey := NormalizeHotkey(NextHotkey)
    if (RegisteredPanelHotkey != "")
        Hotkey, %RegisteredPanelHotkey%, ToggleStatusPanel, Off
    if (NextHotkey != "")
        Hotkey, %NextHotkey%, ToggleStatusPanel, On
    RegisteredPanelHotkey := NextHotkey
}

RegisterSyncToggleHotkey() {
    global RegisteredSyncToggleHotkey, ConfigPath
    IniRead, NextHotkey, %ConfigPath%, sync, syncToggleHotkey,
    NextHotkey := NormalizeHotkey(NextHotkey)
    if (RegisteredSyncToggleHotkey != "")
        Hotkey, %RegisteredSyncToggleHotkey%, ToggleSyncSession, Off
    if (NextHotkey != "")
        Hotkey, %NextHotkey%, ToggleSyncSession, On
    RegisteredSyncToggleHotkey := NextHotkey
}

NormalizeHotkey(value) {
    value := Trim(value)
    if (value = "")
        return ""
    if InStr(value, "^") || InStr(value, "!") || InStr(value, "+") || InStr(value, "#") {
        return value
    }

    normalized := RegExReplace(value, "\s*\+\s*", "+")
    normalized := StrReplace(normalized, " ", "")
    StringLower, normalized, normalized
    parts := StrSplit(normalized, "+")
    modifiers := ""
    key := ""
    for index, part in parts {
        if (part = "")
            continue
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
    if (key = "")
        return ""
    StringUpper, key, key
    return modifiers . key
}

FormatHotkeyForDisplay(value) {
    value := NormalizeHotkey(value)
    if (value = "")
        return ""
    output := ""
    Loop, Parse, value
    {
        token := A_LoopField
        if (token = "^")
            output .= (output = "" ? "" : " + ") . "Ctrl"
        else if (token = "!")
            output .= (output = "" ? "" : " + ") . "Alt"
        else if (token = "+")
            output .= (output = "" ? "" : " + ") . "Shift"
        else if (token = "#")
            output .= (output = "" ? "" : " + ") . "Win"
        else {
            StringUpper, token, token
            output .= (output = "" ? "" : " + ") . token
        }
    }
    return output
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
    global HelperRestartFailures
    HelperRestartFailures := 0
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
