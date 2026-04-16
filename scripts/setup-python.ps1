<#
.SYNOPSIS
  Creates a Python venv and installs dependencies for a given service.
.PARAMETER Service
  Which Python service to set up: "embedder" or "ocr"
#>
param(
    [Parameter(Mandatory)]
    [ValidateSet('embedder', 'ocr')]
    [string]$Service,
    [string]$PythonVersion = '3.12'
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

$uv = Get-Command 'uv' -ErrorAction SilentlyContinue
if ($uv) {
    Write-Host "[setup-python] Creating/updating venv with uv (Python $PythonVersion)..." -ForegroundColor Yellow
    & $uv.Source venv --allow-existing --python $PythonVersion $venvDir
    if ($LASTEXITCODE -ne 0) { Write-Error "uv venv failed."; exit 1 }
} else {
    # Fall back to system Python only when it is a compatible version.
    $python = $null
    foreach ($cmd in @('python3', 'python')) {
        if (Get-Command $cmd -ErrorAction SilentlyContinue) {
            $python = $cmd
            break
        }
    }
    if (-not $python) {
        Write-Error "Python not found. Install uv or Python 3.11/3.12 and ensure it is in PATH."
        exit 1
    }

    $versionText = & $python -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to detect Python version from '$python'."
        exit 1
    }
    if ($versionText -notin @('3.11', '3.12')) {
        Write-Error "Detected Python $versionText from '$python', but '$Service' requires Python 3.11/3.12. Install uv or a compatible Python first."
        exit 1
    }

    if (-not (Test-Path $venvDir)) {
        Write-Host "[setup-python] Creating venv with Python $versionText..." -ForegroundColor Yellow
        & $python -m venv $venvDir
        if ($LASTEXITCODE -ne 0) { Write-Error "Failed to create venv."; exit 1 }
    }
}

# Resolve venv executables
$pythonExe = Join-Path $venvDir 'Scripts\python.exe'
if (-not (Test-Path $pythonExe)) {
    $pythonExe = Join-Path $venvDir 'bin/python'
}
if (-not (Test-Path $pythonExe)) {
    Write-Error "Python executable not found in venv at $venvDir"
    exit 1
}

Write-Host "[setup-python] Installing dependencies..." -ForegroundColor Yellow
if ($uv) {
    & $uv.Source pip install --python $pythonExe -r $reqFile
    if ($LASTEXITCODE -ne 0) { Write-Error "uv pip install failed."; exit 1 }
} else {
    $pip = Join-Path $venvDir 'Scripts\pip.exe'
    if (-not (Test-Path $pip)) {
        $pip = Join-Path $venvDir 'bin/pip'
    }
    & $pip install -r $reqFile
    if ($LASTEXITCODE -ne 0) { Write-Error "pip install failed."; exit 1 }
}

Write-Host "[setup-python] '$Service' environment ready with $pythonExe" -ForegroundColor Green
