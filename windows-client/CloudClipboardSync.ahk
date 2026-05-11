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
global ClientStatus := "初始化中"
global LastSyncResult := "暂无"
global PanelVisible := false

EnsureConfig()
InitGui()
InitTray()
StartHelper()
OnClipboardChange("HandleClipboardChange")
SetTimer, PollEvents, 800
SetTimer, EnsureHelperRunning, 5000
Hotkey, ^!v, ToggleStatusPanel
return

HandleClipboardChange(Type) {
    global ApplyingRemoteClipboard, SyncPaused
    if (Type != 1 || ApplyingRemoteClipboard || SyncPaused)
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
    Gui, Status:Show, AutoSize, Cloud Clipboard 同步状态
    PanelVisible := true
}

HideStatusPanel() {
    global PanelVisible
    Gui, Status:Hide
    PanelVisible := false
}

OpenConfig:
    Run, notepad.exe "%ConfigPath%"
return

OpenWebConsole:
    IniRead, ServerBase, %ConfigPath%, sync, serverBase, http://127.0.0.1:9501
    Run, % ServerBase . "/#/device"
return

SendFilesToAndroid:
    global LastSyncResult
    paths := SelectFilesForAndroid()
    if (paths = "")
        return
    AppendCommand("payload", paths)
    LastSyncResult := "已提交文件通知任务，等待后台上传"
    UpdateGui()
return

ReconnectHelper:
    StopHelper()
    FileDelete, %CommandPath%
    FileDelete, %EventPath%
    LastEventLine := 0
    StartHelper()
return

EnsureHelperRunning:
    global HelperPid
    Process, Exist, %HelperPid%
    if (HelperPid = "" || ErrorLevel = 0)
        StartHelper()
return

TogglePause:
    global SyncPaused
    SyncPaused := !SyncPaused
    status := SyncPaused ? "off" : "on"
    AppendCommand("toggle", status)
    ClientStatus := SyncPaused ? "已暂停同步" : "同步已恢复"
    UpdateGui()
return

ToggleStartup:
    startupLink := A_Startup . "\CloudClipboardSync.lnk"
    if FileExist(startupLink) {
        FileDelete, %startupLink%
        TrayTip, Cloud Clipboard, 已关闭开机启动, 3, 1
    } else {
        FileCreateShortcut, %A_ScriptFullPath%, %startupLink%
        TrayTip, Cloud Clipboard, 已开启开机启动, 3, 1
    }
return

ExitClient:
    AppendCommand("shutdown", "")
    StopHelper()
    ExitApp
return

EnsureConfig() {
    global ConfigPath
    if FileExist(ConfigPath)
        return
    Random, suffix, 1000, 9999
    IniWrite, http://127.0.0.1:9501, %ConfigPath%, sync, serverBase
    IniWrite, , %ConfigPath%, sync, room
    IniWrite, , %ConfigPath%, sync, roomPassword
    IniWrite, Windows 同步端-%suffix%, %ConfigPath%, sync, deviceName
    IniWrite, %A_Now%%suffix%, %ConfigPath%, sync, deviceId
}

InitGui() {
    Gui, Status:New, +AlwaysOnTop +ToolWindow, Cloud Clipboard 同步状态
    Gui, Status:Margin, 16, 16
    Gui, Status:Add, Text, vStatusText w360, 状态：初始化中
    Gui, Status:Add, Text, vRoomText w360, 房间：-
    Gui, Status:Add, Text, vDeviceText w360, 设备：-
    Gui, Status:Add, Text, vPasswordText w360, 房间密码：-
    Gui, Status:Add, Text, vResultText w360, 最近结果：暂无
    Gui, Status:Add, Button, gOpenConfig w110, 打开设置
    Gui, Status:Add, Button, gReconnectHelper x+8 w110, 重新连接
    Gui, Status:Add, Button, gOpenWebConsole x+8 w120, 打开网页管理台
    Gui, Status:Add, Button, gSendFilesToAndroid xm y+12 w360, 发送文件/图片到安卓确认接收
}

UpdateGui() {
    global ConfigPath, ClientStatus, LastSyncResult
    IniRead, RoomName, %ConfigPath%, sync, room,
    IniRead, DeviceName, %ConfigPath%, sync, deviceName, Windows 同步端
    IniRead, RoomPassword, %ConfigPath%, sync, roomPassword,
    masked := RoomPassword = "" ? "未设置" : "已设置"
    GuiControl, Status:, StatusText, 状态：%ClientStatus%
    GuiControl, Status:, RoomText, 房间：%RoomName%
    GuiControl, Status:, DeviceText, 设备：%DeviceName%
    GuiControl, Status:, PasswordText, 房间密码：%masked%
    GuiControl, Status:, ResultText, 最近结果：%LastSyncResult%
}

InitTray() {
    Menu, Tray, NoStandard
    Menu, Tray, Tip, Cloud Clipboard 同步客户端
    Menu, Tray, Add, 显示/隐藏状态面板, ToggleStatusPanel
    Menu, Tray, Add, 打开设置, OpenConfig
    Menu, Tray, Add, 暂停/恢复同步, TogglePause
    Menu, Tray, Add, 重新连接, ReconnectHelper
    Menu, Tray, Add, 发送文件/图片到安卓, SendFilesToAndroid
    Menu, Tray, Add, 打开网页管理台, OpenWebConsole
    Menu, Tray, Add, 开机启动开关, ToggleStartup
    Menu, Tray, Add, 退出, ExitClient
    Menu, Tray, Default, 显示/隐藏状态面板
}

StartHelper() {
    global HelperPid, HelperPath, ConfigPath, CommandPath, EventPath
    Run, %ComSpec% /c powershell -ExecutionPolicy Bypass -File "%HelperPath%" -ConfigPath "%ConfigPath%" -CommandPath "%CommandPath%" -EventPath "%EventPath%",, Hide, HelperPid
}

StopHelper() {
    global HelperPid
    if (HelperPid != "")
        Process, Close, %HelperPid%
    HelperPid := ""
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
