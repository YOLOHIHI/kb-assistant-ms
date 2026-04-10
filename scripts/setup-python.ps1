<#
.SYNOPSIS
  Creates a Python venv and installs dependencies for a given service.
.PARAMETER Service
  Which Python service to set up: "embedder" or "ocr"
#>
param(
    [Parameter(Mandatory)]
    [ValidateSet('embedder', 'ocr')]
    [string]$Service
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')

$serviceMap = @{
    embedder = Join-Path $root 'services\kb-embedder'
    ocr      = Join-Path $root 'services\kb-ocr-service'
}

$serviceDir = $serviceMap[$Service]
$venvDir    = Join-Path $serviceDir '.venv'
$reqFile    = Join-Path $serviceDir 'requirements.txt'

Write-Host "[setup-python] Setting up '$Service' at $serviceDir" -ForegroundColor Cyan

if (-not (Test-Path $reqFile)) {
    Write-Error "requirements.txt not found at $reqFile"
    exit 1
}

# Find python executable
$python = $null
foreach ($cmd in @('python3', 'python')) {
    if (Get-Command $cmd -ErrorAction SilentlyContinue) {
        $python = $cmd
        break
    }
}
if (-not $python) {
    Write-Error "Python not found. Please install Python 3.11+ and ensure it is in PATH."
    exit 1
}

# Create venv if not exists
if (-not (Test-Path $venvDir)) {
    Write-Host "[setup-python] Creating venv..." -ForegroundColor Yellow
    & $python -m venv $venvDir
    if ($LASTEXITCODE -ne 0) { Write-Error "Failed to create venv."; exit 1 }
}

# Activate and install
$pip = Join-Path $venvDir 'Scripts\pip.exe'
if (-not (Test-Path $pip)) {
    $pip = Join-Path $venvDir 'bin/pip'
}

Write-Host "[setup-python] Installing dependencies..." -ForegroundColor Yellow
& $pip install -r $reqFile
if ($LASTEXITCODE -ne 0) { Write-Error "pip install failed."; exit 1 }

Write-Host "[setup-python] '$Service' environment ready." -ForegroundColor Green
