$ErrorActionPreference = 'Stop'

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$backendRoot = Join-Path $projectRoot 'backend'
$envPath = Join-Path $backendRoot '.env.supabase.local'

if (-not (Test-Path $envPath)) {
  throw "Missing $envPath. Copy backend\env.supabase.example or create backend\.env.supabase.local first."
}

Get-Content $envPath | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith('#')) { return }
  $parts = $line.Split('=', 2)
  if ($parts.Length -ne 2) { return }
  [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
}

if (-not $env:SPRING_DATASOURCE_URL) { throw 'Missing SPRING_DATASOURCE_URL in backend\.env.supabase.local.' }
if (-not $env:SPRING_DATASOURCE_USERNAME) { throw 'Missing SPRING_DATASOURCE_USERNAME in backend\.env.supabase.local.' }
if (-not $env:SPRING_DATASOURCE_PASSWORD -or $env:SPRING_DATASOURCE_PASSWORD -eq '<database-password>') {
  throw 'Missing SPRING_DATASOURCE_PASSWORD in backend\.env.supabase.local. Paste the Supabase database password into that file first.'
}

$env:SPRING_PROFILES_ACTIVE = 'prod'
Set-Location $backendRoot
& .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=prod'
