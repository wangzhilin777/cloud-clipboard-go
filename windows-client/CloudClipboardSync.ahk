#NoEnv
;@Ahk2Exe-SetMainIcon assets\cloud-clipboard-sync.ico
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
global ShellMenuScriptPath := A_ScriptDir . "\shell-menu.ps1"
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
global RegisteredDirectSendHotkey := ""
global RegisteredDirectPasteHotkey := ""
global ShellMenuRegistered := false
global HotkeyCaptureSuspended := false
global TrayIconVisible := true
global AdvancedPanelVisible := false
global NoticePopupVisible := false
global NoticeCategory := ""
global NoticePrimaryAction := ""
global NoticeSecondaryAction := ""
global NoticeTertiaryAction := ""
global NoticeAutoHideAt := 0
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
global EditDirectSendHotkey := ""
global EditDirectPasteHotkey := ""
global EditAutoConnectEnabled := 0
global EditStartupEnabled := 0
global EditFileConfirmSeconds := 8
global EditCopyConfirmEnabled := 1
global EditPasteConfirmEnabled := 1
global EditShellMenuEnabled := 1
global EditShellMenuPersistent := 0
global EditNoticeMode := "popup"
global CaptureHotkeyValue := ""
global CaptureHotkeyTarget := ""
global PendingPayloadSignature := ""
global PendingPayloadPaths := ""
global PendingPayloadExpiresAt := 0
global PendingPayloadAction := ""
global PendingPayloadDescription := ""
global PendingPayloadCount := 0
global PendingReceivePayloadId := ""
global PendingReceivePayloadTitle := ""
global PendingReceivePayloadExpiresAt := 0
global PendingReceiveSourceDevice := ""
global PendingReceiveKind := ""
global PendingReceivePasteArmed := false

EnsureConfig()
RefreshRuntimePaths()
InitGui()
InitAdvancedGui()
InitNoticePopup()
InitTray()
RegisterPanelHotkey()
RegisterSyncToggleHotkey()
RegisterTrayIconHotkey()
RegisterDirectSendHotkey()
RegisterDirectPasteHotkey()
if (ShouldShowInitialPanel())
    ShowStatusPanel()
OnClipboardChange("HandleClipboardChange")
Hotkey, ^v, InterceptPasteHotkey, On
SetTimer, PollEvents, 800
SetTimer, EnsureHelperRunning, 2000
TryAutoStartSync()
return

HandleClipboardChange(Type) {
    global ApplyingRemoteClipboard, SyncPaused, HelperActive
    if (ApplyingRemoteClipboard || SyncPaused || !HelperActive)
        return
    if (ClipboardContainsFiles()) {
        paths := GetClipboardFileList()
        if (paths != "") {
            if (IsCopyConfirmationEnabled())
                RequestPayloadConfirmation(paths, "upload", "已复制文件到剪贴板")
            else
                ExecutePayloadAction("upload", paths)
            return
        }
    }
    if (Type = 1) {
        ClipWait, 0.2
        text := Clipboard
        if (text = "")
            return
        AppendCommand("publish", text)
        return
    }
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
        } else if (type = "payloadNotice") {
            HandleIncomingPayloadNotice(Base64Decode(parts[2]))
        } else if (type = "payloadDownloaded") {
            HandleIncomingPayloadDownloaded(Base64Decode(parts[2]))
        } else if (type = "payloadClipboardReady") {
            HandleIncomingPayloadClipboardReady(Base64Decode(parts[2]))
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

NoticePrimaryActionLabel:
    HandleNoticePopupAction(NoticePrimaryAction)
return

NoticeSecondaryActionLabel:
    HandleNoticePopupAction(NoticeSecondaryAction)
return

NoticeTertiaryActionLabel:
    HandleNoticePopupAction(NoticeTertiaryAction)
return

NoticeGuiEscape:
NoticeGuiClose:
    HideNoticePopup()
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
    global EditPanelHotkey, EditSyncToggleHotkey, EditTrayIconHotkey, EditDirectSendHotkey, EditDirectPasteHotkey, EditAutoConnectEnabled, EditStartupEnabled, EditFileConfirmSeconds, EditCopyConfirmEnabled, EditPasteConfirmEnabled, EditShellMenuEnabled, EditShellMenuPersistent, EditNoticeMode, LastSyncResult, HelperActive
    Gui, Status:Submit, NoHide
    Gui, Advanced:Submit, NoHide
    autoConnectValue := EditAutoConnectEnabled ? 1 : 0
    startupValue := EditStartupEnabled ? 1 : 0
    copyConfirmValue := EditCopyConfirmEnabled ? 1 : 0
    pasteConfirmValue := EditPasteConfirmEnabled ? 1 : 0
    shellMenuValue := EditShellMenuEnabled ? 1 : 0
    shellMenuPersistentValue := EditShellMenuPersistent ? 1 : 0
    normalizedNoticeMode := NormalizeNoticeMode(EditNoticeMode)
    normalizedHotkey := NormalizeHotkey(EditPanelHotkey)
    normalizedSyncToggleHotkey := NormalizeHotkey(EditSyncToggleHotkey)
    normalizedTrayIconHotkey := NormalizeHotkey(EditTrayIconHotkey)
    normalizedDirectSendHotkey := NormalizeHotkey(EditDirectSendHotkey)
    normalizedDirectPasteHotkey := NormalizeHotkey(EditDirectPasteHotkey)
    normalizedRuntimeDir := NormalizeRuntimeDirForSave(EditRuntimeDir)
    normalizedFileConfirmSeconds := EditFileConfirmSeconds + 0
    if (normalizedFileConfirmSeconds < 3)
        normalizedFileConfirmSeconds := 3
    if (normalizedFileConfirmSeconds > 30)
        normalizedFileConfirmSeconds := 30
    IniWrite, %EditServerBase%, %ConfigPath%, sync, serverBase
    IniWrite, %EditRoom%, %ConfigPath%, sync, room
    IniWrite, %EditRoomPassword%, %ConfigPath%, sync, roomPassword
    IniWrite, % ResolveDeviceNameForSave(EditDeviceName), %ConfigPath%, sync, deviceName
    IniWrite, % ResolveDeviceIdForSave(EditDeviceId), %ConfigPath%, sync, deviceId
    IniWrite, %normalizedRuntimeDir%, %ConfigPath%, sync, runtimeDir
    IniWrite, %normalizedHotkey%, %ConfigPath%, sync, panelHotkey
    IniWrite, %normalizedSyncToggleHotkey%, %ConfigPath%, sync, syncToggleHotkey
    IniWrite, %normalizedTrayIconHotkey%, %ConfigPath%, sync, trayIconHotkey
    IniWrite, %normalizedDirectSendHotkey%, %ConfigPath%, sync, directSendHotkey
    IniWrite, %normalizedDirectPasteHotkey%, %ConfigPath%, sync, directPasteHotkey
    IniWrite, %autoConnectValue%, %ConfigPath%, sync, autoConnectEnabled
    IniWrite, %startupValue%, %ConfigPath%, sync, startupEnabled
    IniWrite, %normalizedFileConfirmSeconds%, %ConfigPath%, sync, fileConfirmSeconds
    IniWrite, %copyConfirmValue%, %ConfigPath%, sync, copyConfirmEnabled
    IniWrite, %pasteConfirmValue%, %ConfigPath%, sync, pasteConfirmEnabled
    IniWrite, %shellMenuValue%, %ConfigPath%, sync, shellMenuEnabled
    IniWrite, %shellMenuPersistentValue%, %ConfigPath%, sync, shellMenuPersistent
    IniWrite, %normalizedNoticeMode%, %ConfigPath%, sync, noticeMode
    RefreshRuntimePaths()
    RegisterPanelHotkey()
    RegisterSyncToggleHotkey()
    RegisterTrayIconHotkey()
    RegisterDirectSendHotkey()
    RegisterDirectPasteHotkey()
    SyncShellMenuRegistration(true)
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

OpenDirectSendHotkeyCapture:
    global CaptureHotkeyValue, CaptureHotkeyTarget, EditDirectSendHotkey
    Gui, Advanced:Submit, NoHide
    SuspendRegisteredHotkeys()
    CaptureHotkeyTarget := "directSend"
    CaptureHotkeyValue := NormalizeHotkey(EditDirectSendHotkey)
    Gui, HotkeyCapture:Destroy
    Gui, HotkeyCapture:New, +OwnerStatus +AlwaysOnTop +ToolWindow, 录制直发热键
    Gui, HotkeyCapture:Margin, 16, 16
    Gui, HotkeyCapture:Add, Text, w280, 按下要用于直接发送当前选中文件的快捷键；也可以清空为不设置。
    Gui, HotkeyCapture:Add, Hotkey, xm y+12 w280 vCaptureHotkeyValue, %CaptureHotkeyValue%
    Gui, HotkeyCapture:Add, Button, xm y+14 w84 gSaveCapturedHotkey Default, 确认
    Gui, HotkeyCapture:Add, Button, x+8 w84 gClearCapturedHotkey, 清空
    Gui, HotkeyCapture:Add, Button, x+8 w84 gCancelCapturedHotkey, 取消
    Gui, HotkeyCapture:Show, AutoSize, 录制直发热键
return

OpenDirectPasteHotkeyCapture:
    global CaptureHotkeyValue, CaptureHotkeyTarget, EditDirectPasteHotkey
    Gui, Advanced:Submit, NoHide
    SuspendRegisteredHotkeys()
    CaptureHotkeyTarget := "directPaste"
    CaptureHotkeyValue := NormalizeHotkey(EditDirectPasteHotkey)
    Gui, HotkeyCapture:Destroy
    Gui, HotkeyCapture:New, +OwnerStatus +AlwaysOnTop +ToolWindow, 录制直贴热键
    Gui, HotkeyCapture:Margin, 16, 16
    Gui, HotkeyCapture:Add, Text, w280, 按下要用于直接下载并粘贴远端文件的快捷键；也可以清空为不设置。
    Gui, HotkeyCapture:Add, Hotkey, xm y+12 w280 vCaptureHotkeyValue, %CaptureHotkeyValue%
    Gui, HotkeyCapture:Add, Button, xm y+14 w84 gSaveCapturedHotkey Default, 确认
    Gui, HotkeyCapture:Add, Button, x+8 w84 gClearCapturedHotkey, 清空
    Gui, HotkeyCapture:Add, Button, x+8 w84 gCancelCapturedHotkey, 取消
    Gui, HotkeyCapture:Show, AutoSize, 录制直贴热键
return

SaveCapturedHotkey:
    global CaptureHotkeyValue, CaptureHotkeyTarget
    Gui, HotkeyCapture:Submit, NoHide
    displayHotkey := FormatHotkeyForDisplay(CaptureHotkeyValue)
    if (CaptureHotkeyTarget = "syncToggle")
        GuiControl, Advanced:, EditSyncToggleHotkey, %displayHotkey%
    else if (CaptureHotkeyTarget = "directPaste")
        GuiControl, Advanced:, EditDirectPasteHotkey, %displayHotkey%
    else if (CaptureHotkeyTarget = "directSend")
        GuiControl, Advanced:, EditDirectSendHotkey, %displayHotkey%
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
    if (IsCopyConfirmationEnabled())
        RequestPayloadConfirmation(paths, "upload", "已选择文件待发送")
    else
        ExecutePayloadAction("upload", paths)
return

ClearRuntimeCache:
    global CommandPath, EventPath, LastEventLine, LastSyncResult, HelperActive, ApplyingRemoteClipboard
    ApplyingRemoteClipboard := false
    LastEventLine := 0
    ClearPendingPayloadState()
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
            ShowNoticePopup("连接已暂停", "连续 3 次连接失败，已停止自动重连。你可以点开面板检查地址、房间密码或服务端状态。", "打开面板", "openPanel", "稍后再说", "dismiss", "", "", 12000, "connectFailure")
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

DirectSendSelectedFiles:
    global LastSyncResult, HelperActive, SyncPaused
    if (!HelperActive) {
        LastSyncResult := "当前未启动后台同步，请先启动同步"
        UpdateGui()
        return
    }
    if (SyncPaused) {
        LastSyncResult := "当前已暂停同步，请先恢复同步再发送文件"
        UpdateGui()
        return
    }
    paths := GetActiveExplorerSelection()
    if (paths = "") {
        LastSyncResult := "当前没有可直接发送的文件，请先在资源管理器或桌面选中文件"
        UpdateGui()
        ShowActionTip("Cloud Clipboard", "没有读取到当前选中的文件，先在资源管理器或桌面选中文件再按直发热键")
        return
    }
    ExecutePayloadAction("upload", paths)
    ClearPendingPayloadState()
return

DirectPasteLatestPayload:
    if (!TriggerPendingReceiveDownload(true, true)) {
        LastSyncResult := "当前没有待接收的远端文件，暂时无法直接下载并粘贴"
        UpdateGui()
        ShowActionTip("Cloud Clipboard", "当前没有待接收的远端文件，收到新的图片或文件通知后再试")
    }
return

StatusGuiDropFiles:
    paths := FilterDroppedFiles(A_GuiEvent)
    if (paths = "") {
        LastSyncResult := "拖入的内容里没有可发送的文件"
        UpdateGui()
        return
    }
    ExecutePayloadAction("upload", paths)
    ClearPendingPayloadState()
return

ExitClient:
    if (HelperActive)
        AppendCommand("shutdown", "")
    ClearPendingPayloadState()
    StopHelper()
    SyncShellMenuRegistration(true)
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
    IniWrite, ^!c, %ConfigPath%, sync, directSendHotkey
    IniWrite, ^!+v, %ConfigPath%, sync, directPasteHotkey
    IniWrite, 1, %ConfigPath%, sync, autoConnectEnabled
    IniWrite, 0, %ConfigPath%, sync, startupEnabled
    IniWrite, 8, %ConfigPath%, sync, fileConfirmSeconds
    IniWrite, 1, %ConfigPath%, sync, copyConfirmEnabled
    IniWrite, 1, %ConfigPath%, sync, pasteConfirmEnabled
    IniWrite, 1, %ConfigPath%, sync, shellMenuEnabled
    IniWrite, 0, %ConfigPath%, sync, shellMenuPersistent
    IniWrite, popup, %ConfigPath%, sync, noticeMode
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
    IniRead, DirectSendHotkey, %ConfigPath%, sync, directSendHotkey, %missingValue%
    if (DirectSendHotkey = missingValue)
        IniWrite, ^!c, %ConfigPath%, sync, directSendHotkey
    IniRead, DirectPasteHotkey, %ConfigPath%, sync, directPasteHotkey, %missingValue%
    if (DirectPasteHotkey = missingValue)
        IniWrite, ^!+v, %ConfigPath%, sync, directPasteHotkey
    IniRead, AutoConnectEnabled, %ConfigPath%, sync, autoConnectEnabled, %missingValue%
    if (AutoConnectEnabled = missingValue || AutoConnectEnabled = "")
        IniWrite, 1, %ConfigPath%, sync, autoConnectEnabled
    IniRead, StartupEnabled, %ConfigPath%, sync, startupEnabled, %missingValue%
    if (StartupEnabled = missingValue || StartupEnabled = "")
        IniWrite, 0, %ConfigPath%, sync, startupEnabled
    IniRead, FileConfirmSeconds, %ConfigPath%, sync, fileConfirmSeconds, %missingValue%
    if (FileConfirmSeconds = missingValue || FileConfirmSeconds = "")
        IniWrite, 8, %ConfigPath%, sync, fileConfirmSeconds
    IniRead, CopyConfirmEnabled, %ConfigPath%, sync, copyConfirmEnabled, %missingValue%
    if (CopyConfirmEnabled = missingValue || CopyConfirmEnabled = "")
        IniWrite, 1, %ConfigPath%, sync, copyConfirmEnabled
    IniRead, PasteConfirmEnabled, %ConfigPath%, sync, pasteConfirmEnabled, %missingValue%
    if (PasteConfirmEnabled = missingValue || PasteConfirmEnabled = "")
        IniWrite, 1, %ConfigPath%, sync, pasteConfirmEnabled
    IniRead, ShellMenuEnabled, %ConfigPath%, sync, shellMenuEnabled, %missingValue%
    if (ShellMenuEnabled = missingValue || ShellMenuEnabled = "")
        IniWrite, 1, %ConfigPath%, sync, shellMenuEnabled
    IniRead, ShellMenuPersistent, %ConfigPath%, sync, shellMenuPersistent, %missingValue%
    if (ShellMenuPersistent = missingValue || ShellMenuPersistent = "")
        IniWrite, 0, %ConfigPath%, sync, shellMenuPersistent
    IniRead, NoticeMode, %ConfigPath%, sync, noticeMode, %missingValue%
    if (NoticeMode = missingValue || NoticeMode = "")
        IniWrite, popup, %ConfigPath%, sync, noticeMode
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
    Gui, Status:Font, s9 Norm c667085, Segoe UI
    Gui, Status:Add, Text, xm y+12 w500 Center Border, 把文件拖到这里可直接发送到安卓，不走二次复制确认
}

InitAdvancedGui() {
    global EditDeviceId, EditRuntimeDir, EditPanelHotkey, EditSyncToggleHotkey, EditTrayIconHotkey, EditDirectSendHotkey, EditDirectPasteHotkey
    global EditAutoConnectEnabled, EditStartupEnabled, EditFileConfirmSeconds, EditCopyConfirmEnabled, EditPasteConfirmEnabled, EditShellMenuEnabled, EditShellMenuPersistent, EditNoticeMode
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
    Gui, Advanced:Add, Text, xm y+12 w92, 直发热键
    Gui, Advanced:Add, Edit, x+10 yp-3 w256 h24 vEditDirectSendHotkey,
    Gui, Advanced:Add, Button, x+8 yp-1 w104 h28 gOpenDirectSendHotkeyCapture, 录制快捷键
    Gui, Advanced:Add, Text, xm y+12 w92, 直贴热键
    Gui, Advanced:Add, Edit, x+10 yp-3 w256 h24 vEditDirectPasteHotkey,
    Gui, Advanced:Add, Button, x+8 yp-1 w104 h28 gOpenDirectPasteHotkeyCapture, 录制快捷键

    Gui, Advanced:Add, Progress, xm y+18 w470 h1 Disabled cE1E7F0 BackgroundE1E7F0, 100
    Gui, Advanced:Font, s10 Bold c24476B, Segoe UI
    Gui, Advanced:Add, Text, xm y+12 w470, 启动规则
    Gui, Advanced:Font, s9 Norm c344054, Segoe UI
    Gui, Advanced:Add, Text, xm y+14 w92, 确认秒数
    Gui, Advanced:Add, Edit, x+10 yp-3 w120 h24 vEditFileConfirmSeconds,
    Gui, Advanced:Add, Text, x+12 yp+4 w246, 普通复制文件后会先提示，在这个时间内再次复制同一批文件才发送；收到远端文件后，再次粘贴可确认下载。
    Gui, Advanced:Add, CheckBox, xm y+14 w470 vEditCopyConfirmEnabled, 普通复制文件时启用二次确认
    Gui, Advanced:Add, CheckBox, xm y+8 w470 vEditPasteConfirmEnabled, 收到远端文件后启用二次粘贴确认下载
    Gui, Advanced:Add, CheckBox, xm y+8 w470 vEditShellMenuEnabled, 同步可用时自动挂上资源管理器右键菜单
    Gui, Advanced:Add, CheckBox, xm y+8 w470 vEditShellMenuPersistent, 始终保留右键菜单入口，不随同步状态自动摘除
    Gui, Advanced:Add, Text, xm y+14 w92, 通知方式
    Gui, Advanced:Add, DropDownList, x+10 yp-3 w170 vEditNoticeMode Choose1, 轻弹窗|右下角Tip|都显示
    Gui, Advanced:Add, CheckBox, xm y+8 w470 vEditAutoConnectEnabled, 启动客户端后按上次状态自动恢复同步
    Gui, Advanced:Add, CheckBox, xm y+8 w470 vEditStartupEnabled, 跟随 Windows 开机启动本客户端
    Gui, Advanced:Add, Button, xm y+18 w104 h30 gSaveConfig Default, 保存配置
    Gui, Advanced:Add, Button, x+8 w104 h30 gAdvancedGuiClose, 关闭
}

InitNoticePopup() {
    global NoticeTitleLabel, NoticeBodyLabel, NoticePrimaryButton, NoticeSecondaryButton, NoticeTertiaryButton
    Gui, Notice:New, -Caption +ToolWindow +AlwaysOnTop +Border +HwndNoticeGuiHwnd, Cloud Clipboard 提示
    Gui, Notice:Margin, 16, 14
    Gui, Notice:Color, F8FAFC
    Gui, Notice:Font, s10 Bold c1F3A5F, Segoe UI
    Gui, Notice:Add, Text, xm w332 vNoticeTitleLabel, Cloud Clipboard
    Gui, Notice:Font, s9 Norm c344054, Segoe UI
    Gui, Notice:Add, Text, xm y+8 w332 r3 vNoticeBodyLabel, 提示内容
    Gui, Notice:Add, Button, xm y+14 w98 h28 gNoticePrimaryActionLabel vNoticePrimaryButton, 确认
    Gui, Notice:Add, Button, x+8 w98 h28 gNoticeSecondaryActionLabel vNoticeSecondaryButton, 稍后
    Gui, Notice:Add, Button, x+8 w98 h28 gNoticeTertiaryActionLabel vNoticeTertiaryButton, 打开面板
    Gui, Notice:Hide
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
    IniRead, DirectSendHotkey, %ConfigPath%, sync, directSendHotkey, ^!c
    IniRead, DirectPasteHotkey, %ConfigPath%, sync, directPasteHotkey, ^!+v
    IniRead, AutoConnectEnabled, %ConfigPath%, sync, autoConnectEnabled, 1
    IniRead, StartupEnabled, %ConfigPath%, sync, startupEnabled, 0
    IniRead, FileConfirmSeconds, %ConfigPath%, sync, fileConfirmSeconds, 8
    IniRead, CopyConfirmEnabled, %ConfigPath%, sync, copyConfirmEnabled, 1
    IniRead, PasteConfirmEnabled, %ConfigPath%, sync, pasteConfirmEnabled, 1
    IniRead, ShellMenuEnabled, %ConfigPath%, sync, shellMenuEnabled, 1
    IniRead, ShellMenuPersistent, %ConfigPath%, sync, shellMenuPersistent, 0
    IniRead, NoticeMode, %ConfigPath%, sync, noticeMode, popup
    masked := RoomPassword = "" ? "未设置" : "已设置"
    autoResumeText := AutoConnectEnabled = 1 ? "开启" : "关闭"
    shellMenuText := ShellMenuEnabled = 1 ? (ShellMenuPersistent = 1 ? "始终保留" : "自动管理") : "关闭"
    noticeModeText := FormatNoticeModeForDisplay(NoticeMode)
    displayHotkey := FormatHotkeyForDisplay(PanelHotkey)
    displaySyncToggleHotkey := FormatHotkeyForDisplay(SyncToggleHotkey)
    displayTrayIconHotkey := FormatHotkeyForDisplay(TrayIconHotkey)
    displayDirectSendHotkey := FormatHotkeyForDisplay(DirectSendHotkey)
    displayDirectPasteHotkey := FormatHotkeyForDisplay(DirectPasteHotkey)
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
    GuiControl, Advanced:, EditDirectSendHotkey, %displayDirectSendHotkey%
    GuiControl, Advanced:, EditDirectPasteHotkey, %displayDirectPasteHotkey%
    GuiControl, Advanced:, EditAutoConnectEnabled, % AutoConnectEnabled = 1 ? 1 : 0
    GuiControl, Advanced:, EditStartupEnabled, % StartupEnabled = 1 ? 1 : 0
    GuiControl, Advanced:, EditFileConfirmSeconds, %FileConfirmSeconds%
    GuiControl, Advanced:, EditCopyConfirmEnabled, % CopyConfirmEnabled = 1 ? 1 : 0
    GuiControl, Advanced:, EditPasteConfirmEnabled, % PasteConfirmEnabled = 1 ? 1 : 0
    GuiControl, Advanced:, EditShellMenuEnabled, % ShellMenuEnabled = 1 ? 1 : 0
    GuiControl, Advanced:, EditShellMenuPersistent, % ShellMenuPersistent = 1 ? 1 : 0
    GuiControl, Advanced:ChooseString, EditNoticeMode, %noticeModeText%
    GuiControl, Status:, StatusText, 状态：%ClientStatus%
    GuiControl, Status:, RoomText, 房间：%RoomName%
    GuiControl, Status:, DeviceText, 设备：%DeviceName%（%DeviceId%） 面板热键：%displayHotkey%
    GuiControl, Status:, PasswordText, 房间密码：%masked% 自动恢复：%autoResumeText% 右键菜单：%shellMenuText%
    GuiControl, Status:, ResultText, 最近结果：%LastSyncResult% 同步开关键：%displaySyncToggleHotkey% 直发热键：%displayDirectSendHotkey% 直贴热键：%displayDirectPasteHotkey% 通知：%noticeModeText% 运行目录：%runtimeDirText%
    SyncShellMenuRegistration()
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
    Menu, Tray, Add, 直接发送当前选中文件, DirectSendSelectedFiles
    Menu, Tray, Add, 下载并粘贴最近收到的文件, DirectPasteLatestPayload
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
    ClearPendingPayloadState()
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

RegisterDirectSendHotkey() {
    global RegisteredDirectSendHotkey, ConfigPath, LastSyncResult
    IniRead, NextHotkey, %ConfigPath%, sync, directSendHotkey, ^!c
    NextHotkey := NormalizeHotkey(NextHotkey)
    if (RegisteredDirectSendHotkey != "")
        Hotkey, %RegisteredDirectSendHotkey%, DirectSendSelectedFiles, Off UseErrorLevel
    if (NextHotkey != "") {
        Hotkey, %NextHotkey%, DirectSendSelectedFiles, On UseErrorLevel
        if (ErrorLevel) {
            LastSyncResult := "直发热键无效，已忽略当前设置"
            NextHotkey := ""
        }
    }
    RegisteredDirectSendHotkey := NextHotkey
}

RegisterDirectPasteHotkey() {
    global RegisteredDirectPasteHotkey, ConfigPath, LastSyncResult
    IniRead, NextHotkey, %ConfigPath%, sync, directPasteHotkey, ^!+v
    NextHotkey := NormalizeHotkey(NextHotkey)
    if (RegisteredDirectPasteHotkey != "")
        Hotkey, %RegisteredDirectPasteHotkey%, DirectPasteLatestPayload, Off UseErrorLevel
    if (NextHotkey != "") {
        Hotkey, %NextHotkey%, DirectPasteLatestPayload, On UseErrorLevel
        if (ErrorLevel) {
            LastSyncResult := "直贴热键无效，已忽略当前设置"
            NextHotkey := ""
        }
    }
    RegisteredDirectPasteHotkey := NextHotkey
}

SuspendRegisteredHotkeys() {
    global RegisteredPanelHotkey, RegisteredSyncToggleHotkey, RegisteredTrayIconHotkey, RegisteredDirectSendHotkey, RegisteredDirectPasteHotkey, HotkeyCaptureSuspended
    if (HotkeyCaptureSuspended)
        return
    if (RegisteredPanelHotkey != "")
        Hotkey, %RegisteredPanelHotkey%, ToggleStatusPanel, Off UseErrorLevel
    if (RegisteredSyncToggleHotkey != "")
        Hotkey, %RegisteredSyncToggleHotkey%, ToggleSyncSession, Off UseErrorLevel
    if (RegisteredTrayIconHotkey != "")
        Hotkey, %RegisteredTrayIconHotkey%, ToggleTrayIconVisibility, Off UseErrorLevel
    if (RegisteredDirectSendHotkey != "")
        Hotkey, %RegisteredDirectSendHotkey%, DirectSendSelectedFiles, Off UseErrorLevel
    if (RegisteredDirectPasteHotkey != "")
        Hotkey, %RegisteredDirectPasteHotkey%, DirectPasteLatestPayload, Off UseErrorLevel
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
    RegisterDirectSendHotkey()
    RegisterDirectPasteHotkey()
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

IsShellMenuEnabled() {
    global ConfigPath
    IniRead, value, %ConfigPath%, sync, shellMenuEnabled, 1
    return value = 1
}

IsShellMenuPersistent() {
    global ConfigPath
    IniRead, value, %ConfigPath%, sync, shellMenuPersistent, 0
    return value = 1
}

IsShellMenuUsable() {
    global HelperActive, SyncPaused, ClientStatus
    return (HelperActive && !SyncPaused && ClientStatus = "已信任")
}

SyncShellMenuRegistration(force := false) {
    global ShellMenuRegistered
    shouldRegister := false
    if (IsShellMenuEnabled()) {
        if (IsShellMenuPersistent() || IsShellMenuUsable())
            shouldRegister := true
    }
    if (!force && shouldRegister = ShellMenuRegistered)
        return
    if (shouldRegister) {
        RegisterShellMenus()
        ShellMenuRegistered := true
    } else {
        UnregisterShellMenus()
        ShellMenuRegistered := false
    }
}

RegisterShellMenus() {
    global ShellMenuScriptPath
    shellCommand := BuildShellMenuCommand()
    RegWrite, REG_SZ, HKCU\Software\Classes\*\shell\CloudClipboardSyncCopy, , 复制到剪贴板服务器
    RegWrite, REG_SZ, HKCU\Software\Classes\*\shell\CloudClipboardSyncCopy, Icon, %A_ScriptFullPath%
    RegWrite, REG_SZ, HKCU\Software\Classes\*\shell\CloudClipboardSyncCopy\command, , % shellCommand . " -Mode copy -Path ""%1"""
    RegWrite, REG_SZ, HKCU\Software\Classes\Directory\shell\CloudClipboardSyncPaste, , 从剪贴板服务器粘贴到此处
    RegWrite, REG_SZ, HKCU\Software\Classes\Directory\shell\CloudClipboardSyncPaste, Icon, %A_ScriptFullPath%
    RegWrite, REG_SZ, HKCU\Software\Classes\Directory\shell\CloudClipboardSyncPaste\command, , % shellCommand . " -Mode paste -Path ""%1"""
    RegWrite, REG_SZ, HKCU\Software\Classes\Directory\Background\shell\CloudClipboardSyncPaste, , 从剪贴板服务器粘贴到此处
    RegWrite, REG_SZ, HKCU\Software\Classes\Directory\Background\shell\CloudClipboardSyncPaste, Icon, %A_ScriptFullPath%
    RegWrite, REG_SZ, HKCU\Software\Classes\Directory\Background\shell\CloudClipboardSyncPaste\command, , % shellCommand . " -Mode paste -Path ""%V"""
}

UnregisterShellMenus() {
    RegDelete, HKCU\Software\Classes\*\shell\CloudClipboardSyncCopy\command
    RegDelete, HKCU\Software\Classes\*\shell\CloudClipboardSyncCopy
    RegDelete, HKCU\Software\Classes\Directory\shell\CloudClipboardSyncPaste\command
    RegDelete, HKCU\Software\Classes\Directory\shell\CloudClipboardSyncPaste
    RegDelete, HKCU\Software\Classes\Directory\Background\shell\CloudClipboardSyncPaste\command
    RegDelete, HKCU\Software\Classes\Directory\Background\shell\CloudClipboardSyncPaste
}

BuildShellMenuCommand() {
    global ShellMenuScriptPath
    return "powershell.exe -NoProfile -ExecutionPolicy Bypass -File """ . ShellMenuScriptPath . """"
}

NormalizeNoticeMode(value) {
    value := Trim(value)
    if (value = "轻弹窗" || value = "popup" || value = "")
        return "popup"
    if (value = "右下角Tip" || value = "tip")
        return "tip"
    if (value = "都显示" || value = "both")
        return "both"
    return "popup"
}

FormatNoticeModeForDisplay(value) {
    value := NormalizeNoticeMode(value)
    if (value = "tip")
        return "右下角Tip"
    if (value = "both")
        return "都显示"
    return "轻弹窗"
}

ShouldUsePopupNotice() {
    global ConfigPath
    IniRead, value, %ConfigPath%, sync, noticeMode, popup
    value := NormalizeNoticeMode(value)
    return (value = "popup" || value = "both")
}

ShouldUseTipNotice() {
    global ConfigPath
    IniRead, value, %ConfigPath%, sync, noticeMode, popup
    value := NormalizeNoticeMode(value)
    return (value = "tip" || value = "both")
}

AppendCommand(type, payload) {
    global CommandPath
    line := type . "|" . Base64Encode(payload) . "`n"
    FileAppend, %line%, %CommandPath%, UTF-8
}

RequestPayloadConfirmation(paths, action := "upload", reason := "已记录文件") {
    global HelperActive, SyncPaused, LastSyncResult
    if (!HelperActive) {
        LastSyncResult := "当前未启动后台同步，请先启动同步"
        UpdateGui()
        return
    }
    if (SyncPaused) {
        LastSyncResult := "当前已暂停同步，请先恢复同步再发送文件"
        UpdateGui()
        return
    }
    signature := BuildPayloadSignature(paths)
    if (signature = "")
        return
    now := A_TickCount
    if (IsPendingPayloadMatch(signature, action, now)) {
        ExecutePayloadAction(action, paths)
        ClearPendingPayloadState()
        return
    }
    ArmPendingPayload(action, paths, signature, reason)
}

TriggerPendingReceiveDownload(pasteAfterCopy := true, force := false) {
    global PendingReceivePayloadId, PendingReceivePayloadExpiresAt, PendingReceivePayloadTitle, LastSyncResult, PendingReceivePasteArmed
    if (PendingReceivePayloadId = "")
        return false
    if (!force && A_TickCount > PendingReceivePayloadExpiresAt) {
        LastSyncResult := "最近收到的远端文件确认已过期，请等待新的通知后再次粘贴"
        ClearPendingReceiveState()
        UpdateGui()
        return false
    }
    if (!force && IsPasteConfirmationEnabled() && !PendingReceivePasteArmed) {
        PendingReceivePasteArmed := true
        LastSyncResult := "已准备接收远端文件：" . PendingReceivePayloadTitle . "；请在确认秒数内再次粘贴以开始下载"
        UpdateGui()
        ShowActionTip("Cloud Clipboard", "再次按 Ctrl+V 可确认下载远端文件：" . PendingReceivePayloadTitle)
        return true
    }
    mode := pasteAfterCopy ? "clipboardPaste" : "download"
    payload := "{""payloadId"":""" . EscapeJsonString(PendingReceivePayloadId) . """,""mode"":""" . mode . """,""paste"":" . (pasteAfterCopy ? "true" : "false") . "}"
    AppendCommand("payloadReceive", payload)
    LastSyncResult := pasteAfterCopy ? "已开始接收远端文件：" . PendingReceivePayloadTitle : "已开始下载远端文件：" . PendingReceivePayloadTitle
    UpdateGui()
    ShowActionTip("Cloud Clipboard", pasteAfterCopy ? "正在下载并准备粘贴：" . PendingReceivePayloadTitle : "正在下载到本地缓存：" . PendingReceivePayloadTitle)
    return true
}

ArmPendingPayload(action, paths, signature, reason) {
    global PendingPayloadSignature, PendingPayloadPaths, PendingPayloadExpiresAt, PendingPayloadAction
    global PendingPayloadDescription, PendingPayloadCount, LastSyncResult
    seconds := GetFileConfirmSeconds()
    PendingPayloadSignature := signature
    PendingPayloadPaths := paths
    PendingPayloadAction := action
    PendingPayloadDescription := reason
    PendingPayloadCount := CountPayloadPaths(paths)
    PendingPayloadExpiresAt := A_TickCount + seconds * 1000
    LastSyncResult := reason . "；请在 " . seconds . " 秒内再次复制同一批文件，才会真正发送到安卓"
    UpdateGui()
    ShowActionTip("Cloud Clipboard", reason . "，" . seconds . " 秒内再次复制同一批文件即可发送到安卓")
    title := "准备发送文件"
    body := reason . "。`n" . "共 " . PendingPayloadCount . " 个文件；你可以再次复制确认，也可以直接点“立即发送”。"
    ShowNoticePopup(title, body, "立即发送", "sendPendingPayload", "稍后再说", "dismissSendPending", "打开面板", "openPanel", seconds * 1000, "sendConfirm")
    delayMs := seconds * 1000
    SetTimer, ClearPendingPayloadTimer, Off
    SetTimer, ClearPendingPayloadTimer, % -delayMs
}

IsPendingPayloadMatch(signature, action, now) {
    global PendingPayloadSignature, PendingPayloadAction, PendingPayloadExpiresAt
    return (PendingPayloadSignature != "" && PendingPayloadSignature = signature && PendingPayloadAction = action && now <= PendingPayloadExpiresAt)
}

ExecutePayloadAction(action, paths) {
    global LastSyncResult
    if (action = "upload") {
        AppendCommand("payload", paths)
        count := CountPayloadPaths(paths)
        LastSyncResult := "已确认发送 " . count . " 个文件通知，等待后台上传"
        UpdateGui()
        ShowActionTip("Cloud Clipboard", "已确认发送 " . count . " 个文件通知，后台开始上传")
    }
}

ClearPendingPayloadState() {
    global PendingPayloadSignature, PendingPayloadPaths, PendingPayloadExpiresAt, PendingPayloadAction
    global PendingPayloadDescription, PendingPayloadCount
    PendingPayloadSignature := ""
    PendingPayloadPaths := ""
    PendingPayloadExpiresAt := 0
    PendingPayloadAction := ""
    PendingPayloadDescription := ""
    PendingPayloadCount := 0
}

ClearPendingReceiveState() {
    global PendingReceivePayloadId, PendingReceivePayloadTitle, PendingReceivePayloadExpiresAt, PendingReceiveSourceDevice, PendingReceiveKind, PendingReceivePasteArmed
    PendingReceivePayloadId := ""
    PendingReceivePayloadTitle := ""
    PendingReceivePayloadExpiresAt := 0
    PendingReceiveSourceDevice := ""
    PendingReceiveKind := ""
    PendingReceivePasteArmed := false
}

ClearPendingPayloadTimer:
    global PendingPayloadSignature, PendingPayloadExpiresAt, PendingPayloadDescription, LastSyncResult, NoticeCategory
    if (PendingPayloadSignature = "")
        return
    if (A_TickCount < PendingPayloadExpiresAt)
        return
    LastSyncResult := PendingPayloadDescription . "已过期；刚才那批文件未发送，如需继续请重新复制一次"
    ClearPendingPayloadState()
    if (NoticeCategory = "sendConfirm")
        HideNoticePopup()
    UpdateGui()
return

GetFileConfirmSeconds() {
    global ConfigPath
    IniRead, value, %ConfigPath%, sync, fileConfirmSeconds, 8
    value += 0
    if (value < 3)
        value := 3
    if (value > 30)
        value := 30
    return value
}

BuildPayloadSignature(paths) {
    lines := StrSplit(paths, "`n", "`r")
    normalized := ""
    for index, item in lines {
        item := Trim(item)
        if (item = "")
            continue
        StringLower, lowerItem, item
        normalized .= "|" . lowerItem
    }
    return normalized
}

CountPayloadPaths(paths) {
    count := 0
    lines := StrSplit(paths, "`n", "`r")
    for index, item in lines {
        if (Trim(item) != "")
            count++
    }
    return count
}

FilterDroppedFiles(paths) {
    output := ""
    lines := StrSplit(paths, "`n", "`r")
    for index, item in lines {
        item := Trim(item)
        if (item = "")
            continue
        if InStr(FileExist(item), "D")
            continue
        if !FileExist(item)
            continue
        output .= (output = "" ? "" : "`n") . item
    }
    return output
}

IsCopyConfirmationEnabled() {
    global ConfigPath
    IniRead, value, %ConfigPath%, sync, copyConfirmEnabled, 1
    return value = 1
}

IsPasteConfirmationEnabled() {
    global ConfigPath
    IniRead, value, %ConfigPath%, sync, pasteConfirmEnabled, 1
    return value = 1
}

HandleIncomingPayloadNotice(jsonText) {
    global PendingReceivePayloadId, PendingReceivePayloadTitle, PendingReceivePayloadExpiresAt, PendingReceiveSourceDevice, PendingReceiveKind, PendingReceivePasteArmed
    global LastSyncResult
    payloadId := ReadJsonValue(jsonText, "payloadId")
    title := ReadJsonValue(jsonText, "title")
    sourceDevice := ReadJsonValue(jsonText, "sourceDeviceId")
    kind := ReadJsonValue(jsonText, "kind")
    if (payloadId = "")
        return
    seconds := GetFileConfirmSeconds()
    PendingReceivePayloadId := payloadId
    PendingReceivePayloadTitle := title = "" ? "远端文件" : title
    PendingReceiveSourceDevice := sourceDevice
    PendingReceiveKind := kind
    PendingReceivePasteArmed := false
    PendingReceivePayloadExpiresAt := A_TickCount + seconds * 1000
    LastSyncResult := "收到远端" . DescribePayloadKind(kind) . "：" . PendingReceivePayloadTitle . "；可用拖拽、直贴热键，或按 Ctrl+V 进入下载确认"
    UpdateGui()
    ShowActionTip("Cloud Clipboard", "收到远端" . DescribePayloadKind(kind) . "“" . PendingReceivePayloadTitle . "”，按 Ctrl+V 可进入下载确认，直贴热键可直接下载并粘贴")
    title := "收到远端" . DescribePayloadKind(kind)
    body := PendingReceivePayloadTitle . "`n你可以直接下载并准备粘贴，也可以先下载到本地缓存。"
    ShowNoticePopup(title, body, "下载并粘贴", "receivePaste", "下载到缓存", "receiveDownload", "忽略", "dismissReceive", seconds * 1000, "receiveConfirm")
    SetTimer, ClearPendingReceiveTimer, Off
    delayMs := seconds * 1000
    SetTimer, ClearPendingReceiveTimer, % -delayMs
}

HandleIncomingPayloadDownloaded(jsonText) {
    global LastSyncResult
    title := ReadJsonValue(jsonText, "title")
    path := ReadJsonValue(jsonText, "path")
    LastSyncResult := "已下载远端文件到本地：" . title
    UpdateGui()
    ShowActionTip("Cloud Clipboard", "已下载到本地：" . path)
    ShowNoticePopup("文件已下载", title . "`n已保存到：" . path, "打开面板", "openPanel", "知道了", "dismiss", "", "", 7000, "downloaded")
    ClearPendingReceiveState()
}

HandleIncomingPayloadClipboardReady(jsonText) {
    global LastSyncResult
    title := ReadJsonValue(jsonText, "title")
    pasteFlag := ReadJsonValue(jsonText, "paste")
    LastSyncResult := "已准备好粘贴远端文件：" . title
    UpdateGui()
    ClearPendingReceiveState()
    if (pasteFlag = "true") {
        ForwardNativePaste()
    }
}

ClearPendingReceiveTimer:
    global PendingReceivePayloadId, PendingReceivePayloadExpiresAt, PendingReceivePayloadTitle, LastSyncResult, NoticeCategory
    if (PendingReceivePayloadId = "")
        return
    if (A_TickCount < PendingReceivePayloadExpiresAt)
        return
    LastSyncResult := "远端文件“" . PendingReceivePayloadTitle . "”的接收确认已过期"
    ClearPendingReceiveState()
    if (NoticeCategory = "receiveConfirm")
        HideNoticePopup()
    UpdateGui()
return

ReadJsonValue(jsonText, key) {
    pattern := """" . key . """:(?:\s*)(""((?:[^""\\]|\\.)*)""|(true|false|null|-?\d+(?:\.\d+)?))"
    if !RegExMatch(jsonText, pattern, match)
        return ""
    value := match2
    if (value = "") {
        value := match3
    }
    quote := Chr(34)
    slash := Chr(92)
    value := StrReplace(value, slash . quote, quote)
    value := StrReplace(value, slash . slash, slash)
    value := StrReplace(value, slash . "/", "/")
    value := StrReplace(value, slash . "n", "`n")
    value := StrReplace(value, slash . "r", "`r")
    value := StrReplace(value, slash . "t", A_Tab)
    return value
}

EscapeJsonString(value) {
    quote := Chr(34)
    slash := Chr(92)
    value := StrReplace(value, slash, slash . slash)
    value := StrReplace(value, quote, slash . quote)
    value := StrReplace(value, "`r", slash . "r")
    value := StrReplace(value, "`n", slash . "n")
    return value
}

DescribePayloadKind(kind) {
    if (kind = "image")
        return "图片"
    if (kind = "file")
        return "文件"
    return "内容"
}

ClipboardContainsFiles() {
    return DllCall("IsClipboardFormatAvailable", "UInt", 15)
}

GetClipboardFileList() {
    ClipWait, 0.2
    raw := Clipboard
    if (raw = "")
        return ""
    output := ""
    lines := StrSplit(raw, "`n", "`r")
    for index, item in lines {
        item := Trim(item, "`r`n `t""")
        if (item = "")
            continue
        if FileExist(item)
            output .= (output = "" ? "" : "`n") . item
    }
    return output
}

GetActiveExplorerSelection() {
    output := ""
    WinGetClass, activeClass, A
    if (activeClass = "Progman" || activeClass = "WorkerW" || activeClass = "CabinetWClass" || activeClass = "ExploreWClass") {
        shellApp := ComObjCreate("Shell.Application")
        for window in shellApp.Windows {
            try hwnd := window.HWND
            catch
                continue
            if (hwnd != WinExist("A"))
                continue
            try items := window.Document.SelectedItems
            catch
                continue
            count := items.Count
            Loop %count% {
                item := items.Item(A_Index - 1)
                path := item.Path
                if (path = "")
                    continue
                output .= (output = "" ? "" : "`n") . path
            }
            break
        }
    }
    return output
}

ShowActionTip(title, message) {
    if (!ShouldUseTipNotice())
        return
    MouseGetPos, mouseX, mouseY
    ToolTip, %message%, % mouseX + 16, % mouseY + 20
    TrayTip, %title%, %message%, 3, 1
    SetTimer, HideActionToolTip, Off
    SetTimer, HideActionToolTip, -3500
}

ShowNoticePopup(title, body, primaryLabel := "", primaryAction := "", secondaryLabel := "", secondaryAction := "", tertiaryLabel := "", tertiaryAction := "", timeoutMs := 8000, category := "general") {
    global NoticePopupVisible, NoticeCategory, NoticePrimaryAction, NoticeSecondaryAction, NoticeTertiaryAction, NoticeAutoHideAt
    if (!ShouldUsePopupNotice()) {
        ShowActionTip(title, body)
        return
    }
    NoticeCategory := category
    NoticePrimaryAction := primaryAction
    NoticeSecondaryAction := secondaryAction
    NoticeTertiaryAction := tertiaryAction
    GuiControl, Notice:, NoticeTitleLabel, %title%
    GuiControl, Notice:, NoticeBodyLabel, %body%
    UpdateNoticePopupButton("NoticePrimaryButton", primaryLabel)
    UpdateNoticePopupButton("NoticeSecondaryButton", secondaryLabel)
    UpdateNoticePopupButton("NoticeTertiaryButton", tertiaryLabel)
    Gui, Notice:Show, AutoSize NA, Cloud Clipboard 提示
    PositionNoticePopup()
    NoticePopupVisible := true
    NoticeAutoHideAt := A_TickCount + timeoutMs
    SetTimer, HideNoticePopupTimer, Off
    delayMs := -timeoutMs
    SetTimer, HideNoticePopupTimer, %delayMs%
}

UpdateNoticePopupButton(controlName, label) {
    if (label = "") {
        GuiControl, Notice:Hide, %controlName%
        return
    }
    GuiControl, Notice:, %controlName%, %label%
    GuiControl, Notice:Show, %controlName%
}

PositionNoticePopup() {
    SysGet, WorkArea, MonitorWorkArea
    WinGetPos, , , popupW, popupH, Cloud Clipboard 提示 ahk_class AutoHotkeyGUI
    if (popupW = "")
        return
    x := WorkAreaRight - popupW - 20
    y := WorkAreaBottom - popupH - 20
    Gui, Notice:Show, x%x% y%y% NA
}

HideNoticePopup() {
    global NoticePopupVisible, NoticeCategory, NoticePrimaryAction, NoticeSecondaryAction, NoticeTertiaryAction, NoticeAutoHideAt
    SetTimer, HideNoticePopupTimer, Off
    Gui, Notice:Hide
    NoticePopupVisible := false
    NoticeCategory := ""
    NoticePrimaryAction := ""
    NoticeSecondaryAction := ""
    NoticeTertiaryAction := ""
    NoticeAutoHideAt := 0
}

HandleNoticePopupAction(action) {
    global PendingPayloadPaths, PendingPayloadAction
    if (action = "")
        return
    if (action = "sendPendingPayload") {
        if (PendingPayloadPaths != "")
            ExecutePayloadAction(PendingPayloadAction, PendingPayloadPaths)
        ClearPendingPayloadState()
    } else if (action = "dismissSendPending") {
        ClearPendingPayloadState()
    } else if (action = "receivePaste") {
        TriggerPendingReceiveDownload(true, true)
    } else if (action = "receiveDownload") {
        TriggerPendingReceiveDownload(false, true)
    } else if (action = "dismissReceive") {
        ClearPendingReceiveState()
    } else if (action = "openPanel") {
        ShowStatusPanel()
    }
    HideNoticePopup()
}

HideNoticePopupTimer:
    global NoticeAutoHideAt
    if (NoticeAutoHideAt != 0 && A_TickCount < NoticeAutoHideAt)
        return
    HideNoticePopup()
return

HideActionToolTip:
    ToolTip
return

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
InterceptPasteHotkey:
    if (TriggerPendingReceiveDownload(true))
        return
    ForwardNativePaste()
return

ForwardNativePaste() {
    Suspend, On
    SendInput, ^v
    Suspend, Off
}
