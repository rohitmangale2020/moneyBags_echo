[CmdletBinding()]
param(
    [string]$Root,
    [switch]$SkipUi
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Root)) {
    $Root = Split-Path -Parent $PSScriptRoot
}
$logDirectory = Join-Path $Root 'logs'
$uiDirectory = Join-Path $Root 'UI\BankingPortal'
$services = @(
    @{ Name = 'discovery-service'; Port = 8761 },
    @{ Name = 'users'; Port = 8081 },
    @{ Name = 'customers'; Port = 8082 },
    @{ Name = 'products'; Port = 8090 },
    @{ Name = 'accounts'; Port = 8084 },
    @{ Name = 'transactions'; Port = 8085 },
    @{ Name = 'audit'; Port = 8086 },
    @{ Name = 'security-service'; Port = 8087 },
    @{ Name = 'api-gateway-service'; Port = 8080 }
)

function Require-Command([string]$Name, [string]$Help) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name was not found. $Help"
    }
}

function Start-LoggedProcess([string]$Name, [string]$WorkingDirectory, [string]$Command) {
    $log = Join-Path $logDirectory "$Name.log"
    # cmd.exe combines stdout and stderr; Start-Process cannot redirect both to one file.
    $process = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "$Command > `"$log`" 2>&1" -WorkingDirectory $WorkingDirectory -WindowStyle Hidden -PassThru
    Write-Host "Started $Name (PID $($process.Id)); log: $log"
}

function Import-DotEnv {
    $envFile = Join-Path $Root '.env'
    if (-not (Test-Path $envFile)) { return }

    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*#' -or $line -notmatch '^\s*([^=\s]+)\s*=\s*(.*?)\s*$') { continue }
        $name, $value = $matches[1], $matches[2]
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }

    # Existing Spring configurations use these names; .env.example uses the clearer underscored names.
    if (-not $env:DBURL) { $env:DBURL = $env:DB_URL }
    if (-not $env:DBUSER) { $env:DBUSER = $env:DB_USER }
    if (-not $env:DBPASSWORD) { $env:DBPASSWORD = $env:DB_PASSWORD }
}

function Test-PortInUse([int]$Port) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}

Require-Command 'mvn' 'Install Apache Maven and make mvn available on PATH.'
Import-DotEnv
foreach ($name in 'DBURL', 'DBUSER', 'DBPASSWORD') {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw "Database setting $name is missing. Copy .env.example to .env and enter your Oracle connection values."
    }
}
if (-not $SkipUi) {
    Require-Command 'ojet' 'Install Oracle JET tooling (npm install -g @oracle/ojet-cli), or rerun with -SkipUi.'
    if (-not (Test-Path (Join-Path $uiDirectory 'node_modules'))) {
        throw "UI dependencies are missing. Run 'npm ci' in $uiDirectory once, then retry."
    }
}

New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null

Write-Host 'Starting discovery service first...'
if (Test-PortInUse 8761) {
    Write-Host 'discovery-service is already listening on port 8761; keeping it running.'
} else {
    Start-LoggedProcess 'discovery-service' $Root 'mvn -pl discovery-service spring-boot:run'
    Start-Sleep -Seconds 5
}

foreach ($service in $services | Where-Object { $_.Name -ne 'discovery-service' }) {
    if (Test-PortInUse $service.Port) {
        Write-Host "$($service.Name) is already listening on port $($service.Port); keeping it running."
    } else {
        Start-LoggedProcess $service.Name $Root "mvn -pl $($service.Name) spring-boot:run"
    }
}

if (-not $SkipUi) {
    if (Test-PortInUse 8000) {
        Write-Host 'ui is already listening on port 8000; keeping it running.'
    } else {
        Start-LoggedProcess 'ui' $uiDirectory 'ojet serve --server-port 8000'
    }
}

Write-Host ''
Write-Host 'Launch requested for every service.'
if (-not $SkipUi) { Write-Host 'UI: http://localhost:8000' }
Write-Host 'API gateway: http://localhost:8080'
Write-Host 'Run .\scripts\health-check.ps1 to check backend readiness.'
