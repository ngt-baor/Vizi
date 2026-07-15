$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..\backend'))

function Read-EnvValue([string]$name) {
  $value = [Environment]::GetEnvironmentVariable($name, 'Process')
  if (-not $value) { $value = [Environment]::GetEnvironmentVariable($name, 'User') }
  if (-not $value) { $value = [Environment]::GetEnvironmentVariable($name, 'Machine') }
  return $value
}

$env:SPRING_PROFILES_ACTIVE = 'local'
$env:SPRING_DATASOURCE_URL = Read-EnvValue 'SPRING_DATASOURCE_URL'
$env:SPRING_DATASOURCE_USERNAME = Read-EnvValue 'SPRING_DATASOURCE_USERNAME'
$env:SPRING_DATASOURCE_PASSWORD = Read-EnvValue 'SPRING_DATASOURCE_PASSWORD'

if (-not $env:SPRING_DATASOURCE_URL -or -not $env:SPRING_DATASOURCE_USERNAME -or -not $env:SPRING_DATASOURCE_PASSWORD) {
  throw 'Missing SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, or SPRING_DATASOURCE_PASSWORD in environment variables.'
}

& .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
