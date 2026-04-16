<#
.SYNOPSIS
  Ensures the local kb-embedder service is available on port 8090.
.DESCRIPTION
  - If http://localhost:8090/health is already reachable, exits successfully.
  - If the embedder venv is missing, creates it with setup-python.ps1.
  - Starts uvicorn in the background and waits until the health endpoint is ready.
#>
param(
    [int]$Port = 8090,
    [int]$StartupTimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$serviceDir = Join-Path $root 'services\kb-embedder'
$venvPython = Join-Path $serviceDir '.venv\Scripts\python.exe'
$healthUrl = "http://localhost:$Port/health"
$runDir = Join-Path $root 'run'
$stdoutLog = Join-Path $runDir 'kb-embedder.stdout.log'
$stderrLog = Join-Path $runDir 'kb-embedder.stderr.log'

function Test-EmbedderHealth {
    try {
        $resp = Invoke-WebRequest -UseBasicParsing $healthUrl -TimeoutSec 5
        return $resp.StatusCode -ge 200 -and $resp.StatusCode -lt 300
    } catch {
        return $false
    }
}

Write-Host "[ensure-embedder] Checking $healthUrl ..." -ForegroundColor Cyan
if (Test-EmbedderHealth) {
    Write-Host "[ensure-embedder] kb-embedder is already healthy." -ForegroundColor Green
    exit 0
}

if (-not (Test-Path $venvPython)) {
    Write-Host "[ensure-embedder] Venv not found. Preparing Python environment..." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'setup-python.ps1') -Service embedder
    if ($LASTEXITCODE -ne 0) { Write-Error "setup-python.ps1 failed."; exit 1 }
}

if (-not (Test-Path $venvPython)) {
    Write-Error "Embedder python executable not found at $venvPython"
    exit 1
}

New-Item -ItemType Directory -Force -Path $runDir | Out-Null

Write-Host "[ensure-embedder] Starting kb-embedder on port $Port ..." -ForegroundColor Yellow
$proc = Start-Process `
    -FilePath $venvPython `
    -ArgumentList @('-m', 'uvicorn', 'app.main:app', '--host', '0.0.0.0', '--port', "$Port") `
    -WorkingDirectory $serviceDir `
    -PassThru `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog

$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    if ($proc.HasExited) {
        Write-Error "kb-embedder exited early with code $($proc.ExitCode). See $stderrLog"
        exit 1
    }
    if (Test-EmbedderHealth) {
        Write-Host "[ensure-embedder] kb-embedder is healthy on port $Port (PID=$($proc.Id))." -ForegroundColor Green
        Write-Host "[ensure-embedder] Logs: $stdoutLog / $stderrLog" -ForegroundColor DarkGray
        exit 0
    }
}

try {
    if (-not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
    }
} catch {}

Write-Error "kb-embedder did not become healthy within $StartupTimeoutSeconds seconds. See $stderrLog"
exit 1
