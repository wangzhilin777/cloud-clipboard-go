param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('copy', 'paste')]
    [string]$Mode,
    [Parameter(Mandatory = $true)]
    [string]$Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$configPath = Join-Path $scriptDir 'config.ini'

function Get-IniValue {
    param(
        [string]$IniPath,
        [string]$Section,
        [string]$Key,
        [string]$Default = ''
    )
    if (-not (Test-Path -LiteralPath $IniPath)) { return $Default }
    $currentSection = ''
    foreach ($line in Get-Content -LiteralPath $IniPath -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed -match '^\[(.+)\]$') {
            $currentSection = $matches[1]
            continue
        }
        if ($currentSection -eq $Section -and $trimmed -match "^(?<key>[^=]+?)=(?<value>.*)$") {
            if ($matches['key'].Trim() -eq $Key) {
                return $matches['value'].Trim()
            }
        }
    }
    return $Default
}

function Resolve-RuntimeDir {
    param([string]$Value)
    $trimmed = $Value.Trim()
    if (-not $trimmed) {
        return $scriptDir
    }
    $normalized = $trimmed -replace '/', '\'
    if ($normalized -match '^[A-Za-z]:\\' -or $normalized.StartsWith('\\')) {
        return $normalized.TrimEnd('\')
    }
    return (Join-Path $scriptDir $normalized).TrimEnd('\')
}

function Append-Command {
    param(
        [string]$CommandPath,
        [string]$Type,
        [string]$Payload
    )
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Payload))
    Add-Content -LiteralPath $CommandPath -Value "$Type|$encoded" -Encoding UTF8
}

function Show-Info {
    param(
        [string]$Message,
        [string]$Title = 'Cloud Clipboard'
    )
    [void][System.Windows.Forms.MessageBox]::Show($Message, $Title, [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Information)
}

function Get-LatestPayloadId {
    param([string]$EventPath)
    if (-not (Test-Path -LiteralPath $EventPath)) {
        return ''
    }
    $lines = Get-Content -LiteralPath $EventPath -Encoding UTF8
    for ($i = $lines.Count - 1; $i -ge 0; $i--) {
        $line = $lines[$i].Trim()
        if (-not $line.StartsWith('payloadNotice|')) {
            continue
        }
        $encoded = $line.Substring('payloadNotice|'.Length)
        if (-not $encoded) {
            continue
        }
        try {
            $json = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($encoded))
            $obj = $json | ConvertFrom-Json
            if ($obj.payloadId) {
                return [string]$obj.payloadId
            }
        } catch {
        }
    }
    return ''
}

function Get-LatestStatus {
    param([string]$EventPath)
    if (-not (Test-Path -LiteralPath $EventPath)) {
        return ''
    }
    $lines = Get-Content -LiteralPath $EventPath -Encoding UTF8
    for ($i = $lines.Count - 1; $i -ge 0; $i--) {
        $line = $lines[$i].Trim()
        if ($line.StartsWith('status|')) {
            return $line.Substring('status|'.Length)
        }
    }
    return ''
}

$runtimeDir = Resolve-RuntimeDir (Get-IniValue -IniPath $configPath -Section 'sync' -Key 'runtimeDir' -Default '')
$commandPath = Join-Path $runtimeDir 'commands.log'
$eventPath = Join-Path $runtimeDir 'events.log'
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
$latestStatus = Get-LatestStatus -EventPath $eventPath
if ($latestStatus -ne '已信任') {
    Show-Info '当前同步还不可用，请先启动客户端并在设备获批后再使用右键菜单。'
    exit 1
}

if ($Mode -eq 'copy') {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Show-Info '当前只支持从文件右键直接发送到剪贴板服务器。'
        exit 1
    }
    Append-Command -CommandPath $commandPath -Type 'payload' -Payload $Path
    exit 0
}

if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
    Show-Info '没有找到可用于接收文件的目标目录。'
    exit 1
}

$payloadId = Get-LatestPayloadId -EventPath $eventPath
if (-not $payloadId) {
    Show-Info '当前没有可接收的远端图片或文件。'
    exit 1
}

$request = @{
    payloadId = $payloadId
    mode = 'directory'
    paste = $false
    targetDir = $Path
} | ConvertTo-Json -Compress
Append-Command -CommandPath $commandPath -Type 'payloadReceive' -Payload $request
