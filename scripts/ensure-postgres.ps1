<#
.SYNOPSIS
  Ensures a PostgreSQL instance is reachable on localhost:5432.
  If not, attempts to start the dev Docker Compose stack.
#>
$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$composeFile = Join-Path $root 'docker-compose.dev.yml'

function Test-TcpPort {
    param([string]$Hostname = 'localhost', [int]$Port = 5432, [int]$TimeoutMs = 2000)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $result = $client.BeginConnect($Hostname, $Port, $null, $null)
        $success = $result.AsyncWaitHandle.WaitOne($TimeoutMs)
        if ($success) { $client.EndConnect($result) }
        $client.Close()
        return $success
    } catch {
        return $false
    }
}

Write-Host "[ensure-postgres] Checking localhost:5432..." -ForegroundColor Cyan

if (Test-TcpPort -Port 5432) {
    Write-Host "[ensure-postgres] PostgreSQL is reachable." -ForegroundColor Green
    exit 0
}

Write-Host "[ensure-postgres] Port 5432 not reachable. Starting Docker Compose..." -ForegroundColor Yellow

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker is not installed or not in PATH. Please start PostgreSQL manually on port 5432."
    exit 1
}

docker compose -f $composeFile up -d
if ($LASTEXITCODE -ne 0) {
    Write-Error "docker compose up failed."
    exit 1
}

# Wait for PostgreSQL to become ready
$maxWait = 30
for ($i = 1; $i -le $maxWait; $i++) {
    Start-Sleep -Seconds 1
    if (Test-TcpPort -Port 5432) {
        Write-Host "[ensure-postgres] PostgreSQL is ready." -ForegroundColor Green
        exit 0
    }
    Write-Host "  Waiting... ($i/$maxWait)" -ForegroundColor DarkGray
}

Write-Error "PostgreSQL did not become ready within ${maxWait}s."
exit 1
