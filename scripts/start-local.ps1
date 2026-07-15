$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

$pgCtl = 'C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe'
if (-not (Test-Path $pgCtl)) {
  throw "PostgreSQL pg_ctl not found at $pgCtl"
}

$pgData = Join-Path $root '.codex-work\postgres\data'
$pgLog = Join-Path $root '.codex-work\postgres\postgres.log'
$runDir = Join-Path $root '.codex-work\local-run'
New-Item -ItemType Directory -Force -Path (Split-Path $pgLog), $runDir | Out-Null

& $pgCtl status -D $pgData *> $null
if ($LASTEXITCODE -ne 0) {
  & $pgCtl start -D $pgData -o '"-p 55432 -h 127.0.0.1"' -l $pgLog
}

$backendLog = Join-Path $runDir 'backend.log'
$frontendLog = Join-Path $runDir 'frontend.log'

if (-not (Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue)) {
  $backendScript = Join-Path $PSScriptRoot 'run-backend-local.ps1'
  $backendCommand = "powershell -NoProfile -ExecutionPolicy Bypass -File `"$backendScript`" > `"$backendLog`" 2>&1"
  Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $backendCommand -WindowStyle Hidden
}

if (-not (Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue)) {
  $frontendCommand = "cd /d `"$root\frontend`" && npm run dev -- --host 127.0.0.1 --port 5173 > `"$frontendLog`" 2>&1"
  Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $frontendCommand -WindowStyle Hidden
}

Start-Sleep -Seconds 8
$health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/health' -TimeoutSec 5
$frontend = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5173' -TimeoutSec 5
Write-Output "Backend: $($health.status) $($health.service)"
Write-Output "Frontend: HTTP $($frontend.StatusCode)"
Write-Output 'Open: http://127.0.0.1:5173/templates'
