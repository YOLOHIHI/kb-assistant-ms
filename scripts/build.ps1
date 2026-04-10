$ErrorActionPreference = 'Stop'

$root     = Resolve-Path (Join-Path $PSScriptRoot '..')
$adminDir = Join-Path (Join-Path $root 'apps') 'admin'
$chatDir  = Join-Path (Join-Path $root 'apps') 'chat'
$homeDir  = Join-Path (Join-Path $root 'apps') 'home'
$adminOut = Join-Path $root 'services\kb-gateway\src\main\resources\static\admin'
$chatOut  = Join-Path $root 'services\kb-gateway\src\main\resources\static\chat-app'
$homeOut  = Join-Path $root 'services\kb-gateway\src\main\resources\static'

# Avoid pnpm self-update checks retrying against the public registry and making
# the second build step look hung on restricted networks.
$env:PNPM_DISABLE_SELF_UPDATE_CHECK = '1'

Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  KB Assistant - 前端构建" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan

# ── 0. 根目录 pnpm install（安装所有 workspace 依赖）───────────────────
Write-Host ""
Write-Host "[0/3] 安装 workspace 依赖..." -ForegroundColor Yellow
Push-Location (Join-Path $PSScriptRoot '..')
try {
    pnpm install --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { throw "pnpm install 失败" }
} finally { Pop-Location }

# ── 1. 管理后台 (apps/admin/) ─────────────────────────────────────────
Write-Host ""
Write-Host "[1/3] 构建管理后台..." -ForegroundColor Yellow
if (Test-Path (Join-Path $adminDir '.next')) { Remove-Item -Recurse -Force (Join-Path $adminDir '.next') }
Push-Location $adminDir
try {
    pnpm run build
    if ($LASTEXITCODE -ne 0) { throw "构建失败" }
} finally { Pop-Location }

if (Test-Path $adminOut) { Remove-Item -Recurse -Force $adminOut }
New-Item -ItemType Directory -Force -Path $adminOut | Out-Null
Copy-Item -Recurse -Force "$adminDir\out\*" "$adminOut\"
Write-Host "  完成" -ForegroundColor Green

# ── 2. 聊天界面 (apps/chat/) ──────────────────────────────────────────
Write-Host ""
Write-Host "[2/3] 构建聊天界面..." -ForegroundColor Yellow
if (Test-Path (Join-Path $chatDir '.next')) { Remove-Item -Recurse -Force (Join-Path $chatDir '.next') }
Push-Location $chatDir
try {
    pnpm run build
    if ($LASTEXITCODE -ne 0) { throw "构建失败" }
} finally { Pop-Location }

if (Test-Path $chatOut) { Remove-Item -Recurse -Force $chatOut }
New-Item -ItemType Directory -Force -Path $chatOut | Out-Null
Copy-Item -Recurse -Force "$chatDir\out\*" "$chatOut\"
Write-Host "  完成" -ForegroundColor Green

# ── 3. 首页 (apps/home/) ──────────────────────────────────────────────
Write-Host ""
Write-Host "[3/3] 构建首页..." -ForegroundColor Yellow
if (Test-Path (Join-Path $homeDir '.next')) { Remove-Item -Recurse -Force (Join-Path $homeDir '.next') }
Push-Location $homeDir
try {
    pnpm run build
    if ($LASTEXITCODE -ne 0) { throw "构建失败" }
} finally { Pop-Location }

$nextDir = Join-Path $homeOut '_next'
if (Test-Path $nextDir) { Remove-Item -Recurse -Force $nextDir }
Copy-Item -Recurse -Force "$homeDir\out\*" "$homeOut\"
Write-Host "  完成" -ForegroundColor Green

# ── 完成提示 ──────────────────────────────────────────────────────────
Write-Host ""
Write-Host "======================================" -ForegroundColor Green
Write-Host "  构建完成！" -ForegroundColor Green
Write-Host "  在 IntelliJ IDEA 中运行项目" -ForegroundColor Green
Write-Host "  然后打开: http://localhost:8080/" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
