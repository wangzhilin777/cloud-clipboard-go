$ErrorActionPreference = 'Stop'

Write-Host '正在重启资源管理器并清理图标缓存...'
Get-Process explorer -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Milliseconds 800

$iconCachePaths = @(
    "$env:LOCALAPPDATA\IconCache.db",
    "$env:LOCALAPPDATA\Microsoft\Windows\Explorer\iconcache*",
    "$env:LOCALAPPDATA\Microsoft\Windows\Explorer\thumbcache*"
)

foreach ($pattern in $iconCachePaths) {
    Get-ChildItem -Path $pattern -Force -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
}

Start-Process explorer.exe
Write-Host '图标缓存已尝试刷新。'
