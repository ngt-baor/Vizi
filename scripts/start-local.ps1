param(
  [switch]$OpenBrowser
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

$postgresPort = 55432
$backendPort = 18080
$frontendPort = 5176
$backendHealthUrl = "http://127.0.0.1:$backendPort/api/health"
$frontendUrl = "http://127.0.0.1:$frontendPort"
$frontendOpenUrl = "$frontendUrl/templates"

function Test-Port([int]$port) {
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $connect = $client.ConnectAsync('127.0.0.1', $port)
    return $connect.Wait(1000) -and $client.Connected
  } catch {
    return $false
  } finally {
    $client.Dispose()
  }
}

function Wait-ForHttp([string]$uri, [int]$timeoutSeconds) {
  $deadline = (Get-Date).AddSeconds($timeoutSeconds)
  do {
    try {
      $response = Invoke-WebRequest -UseBasicParsing -Uri $uri -TimeoutSec 3
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
        return $response
      }
    } catch {
      # The service may still be compiling or starting.
    }
    Start-Sleep -Seconds 1
  } while ((Get-Date) -lt $deadline)
  return $null
}

$pgCtl = 'C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe'
if (-not (Test-Path $pgCtl)) {
  throw "PostgreSQL pg_ctl not found at $pgCtl"
}

$pgData = Join-Path $root '.codex-work\postgres\data'
$pgLog = Join-Path $root '.codex-work\postgres\postgres.log'
$runDir = Join-Path $root '.codex-work\local-run'
$backendLog = Join-Path $runDir 'backend.log'
$backendErrorLog = Join-Path $runDir 'backend-error.log'
$frontendLog = Join-Path $runDir 'frontend.log'
$frontendErrorLog = Join-Path $runDir 'frontend-error.log'

if (-not (Test-Path $pgData)) {
  throw "Local PostgreSQL data directory not found at $pgData"
}
New-Item -ItemType Directory -Force -Path (Split-Path $pgLog), $runDir | Out-Null

if (-not (Test-Port $postgresPort)) {
  Write-Output "Starting PostgreSQL on port $postgresPort..."
  & $pgCtl start -D $pgData -o "-p $postgresPort -h 127.0.0.1" -l $pgLog
  if ($LASTEXITCODE -ne 0 -and -not (Test-Port $postgresPort)) {
    throw "PostgreSQL could not be started. See $pgLog"
  }
  $postgresReady = $false
  for ($attempt = 0; $attempt -lt 20 -and -not $postgresReady; $attempt++) {
    Start-Sleep -Seconds 1
    $postgresReady = Test-Port $postgresPort
  }
  if (-not $postgresReady) {
    throw "PostgreSQL did not become available on port $postgresPort. See $pgLog"
  }
} else {
  Write-Output "PostgreSQL port $postgresPort is already in use."
}

$env:PORT = [string]$backendPort
$env:APP_CORS_ALLOWED_ORIGINS = 'http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174,http://localhost:5176,http://127.0.0.1:5176'

if (-not (Test-Port $backendPort)) {
  Write-Output "Starting backend on port $backendPort..."
  $backendScript = Join-Path $PSScriptRoot 'run-backend-local.ps1'
  Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $backendScript) -WorkingDirectory $root -RedirectStandardOutput $backendLog -RedirectStandardError $backendErrorLog -WindowStyle Hidden | Out-Null
} else {
  Write-Output "Backend port $backendPort is already in use."
}

$frontendDir = Join-Path $root 'frontend'
if (-not (Test-Port $frontendPort)) {
  Write-Output "Starting frontend on port $frontendPort..."
  Start-Process -FilePath 'npm.cmd' -ArgumentList @('run', 'dev', '--', '--host', '127.0.0.1', '--port', [string]$frontendPort) -WorkingDirectory $frontendDir -RedirectStandardOutput $frontendLog -RedirectStandardError $frontendErrorLog -WindowStyle Hidden | Out-Null
} else {
  Write-Output "Frontend port $frontendPort is already in use."
}

$backendResponse = Wait-ForHttp $backendHealthUrl 120
if ($null -eq $backendResponse) {
  throw "Backend did not become healthy within 120 seconds. See $backendLog and $backendErrorLog"
}

$frontendResponse = Wait-ForHttp $frontendUrl 60
if ($null -eq $frontendResponse) {
  throw "Frontend did not become available within 60 seconds. See $frontendLog and $frontendErrorLog"
}

Write-Output "PostgreSQL: 127.0.0.1:$postgresPort"
Write-Output "Backend: $backendHealthUrl (HTTP $($backendResponse.StatusCode))"
Write-Output "Frontend: $frontendUrl (HTTP $($frontendResponse.StatusCode))"
Write-Output "Open: $frontendOpenUrl"

if ($OpenBrowser) {
  Start-Process $frontendOpenUrl | Out-Null
}
