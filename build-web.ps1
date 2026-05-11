param(
    [switch]$SkipInstall,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

function Show-Help {
    Write-Host "用法: .\build-web.ps1 [选项]" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "选项:" -ForegroundColor Yellow
    Write-Host "  -SkipInstall   跳过 npm install，直接执行构建"
    Write-Host "  -Help          显示此帮助信息"
    Write-Host ""
    Write-Host "说明:" -ForegroundColor Green
    Write-Host "  1. 构建 client/dist"
    Write-Host "  2. 自动同步到 server/static"
    Write-Host "  3. 自动同步到 server-node/static"
    Write-Host "  4. 自动同步到 cloud-clip/lib/static（供 go run -tags embed / gomobile bind 使用）"
    Write-Host ""
}

if ($Help) {
    Show-Help
    exit 0
}

function Test-Command {
    param([string]$Command)
    $null = Get-Command $Command -ErrorAction SilentlyContinue
    return $?
}

if (-not (Test-Command "node")) {
    throw "未检测到 node，请先安装 Node.js。"
}

if (-not (Test-Command "npm")) {
    throw "未检测到 npm，请先安装 Node.js。"
}

$rootDir = Get-Location
$clientDir = Join-Path $rootDir "client"

if (-not (Test-Path $clientDir)) {
    throw "找不到 client 目录：$clientDir"
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  构建 Cloud Clipboard Web 前端" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Push-Location $clientDir
try {
    if (-not $SkipInstall) {
        Write-Host "[1/2] 安装前端依赖..." -ForegroundColor Yellow
        & npm install
        if ($LASTEXITCODE -ne 0) {
            throw "npm install 执行失败"
        }
    } else {
        Write-Host "[1/2] 已跳过 npm install" -ForegroundColor Yellow
    }

    Write-Host "[2/2] 构建前端并同步静态资源..." -ForegroundColor Yellow
    & npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "npm run build 执行失败"
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[完成] 前端构建成功，静态资源已同步到：" -ForegroundColor Green
Write-Host "  - server/static" -ForegroundColor Green
Write-Host "  - server-node/static" -ForegroundColor Green
Write-Host "  - cloud-clip/lib/static" -ForegroundColor Green
