$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$source = Join-Path $scriptDir 'CloudClipboardSync.ahk'
$icon = Join-Path $scriptDir 'assets\cloud-clipboard-sync.ico'
$output = Join-Path $scriptDir 'CloudClipboardSync.exe'

$compilerCandidates = @(
    'C:\Program Files\AutoHotkey\Compiler\Ahk2Exe.exe',
    'C:\Program Files\AutoHotkey\Ahk2Exe.exe'
)
$binCandidates = @(
    'C:\Program Files\AutoHotkey\Compiler\Unicode 64-bit.bin',
    'C:\Program Files\AutoHotkey\Compiler\AutoHotkeySC.bin'
)

$compiler = $compilerCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
$binFile = $binCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $compiler) {
    throw '未找到 Ahk2Exe.exe，请先安装 AutoHotkey 1.1 编译器。'
}
if (-not $binFile) {
    throw '未找到 AutoHotkey 编译 bin 文件，请检查 AutoHotkey 安装。'
}
if (-not (Test-Path $icon)) {
    throw "未找到图标文件：$icon"
}

if (Test-Path $output) {
    Remove-Item -LiteralPath $output -Force
}

& $compiler /in $source /out $output /icon $icon /bin $binFile

Write-Host "已生成：$output"
