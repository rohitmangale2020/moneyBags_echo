param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [switch]$SkipBuild,
    [int]$StartupTimeoutSeconds = 240
)

$ErrorActionPreference = 'Stop'
$logDirectory = Join-Path $Root 'logs'
$pidFile = Join-Path $logDirectory 'platform-processes.json'

$services = @(
    [pscustomobject]@{ Module = 'discovery-service'; App = $null; Port = 8761; Jar = 'discovery-service-*.jar' },
    [pscustomobject]@{ Module = 'users'; App = 'USERS-SERVICE'; Port = 8081; Jar = 'users-*.jar' },
    [pscustomobject]@{ Module = 'customers'; App = 'CUSTOMERS-SERVICE'; Port = 8082; Jar = 'customers-*.jar' },
    [pscustomobject]@{ Module = 'products'; App = 'PRODUCTS-SERVICE'; Port = 8090; Jar = 'products-*.jar' },
    [pscustomobject]@{ Module = 'accounts'; App = 'ACCOUNTS-SERVICE'; Port = 8084; Jar = 'accounts-*.jar' },
    [pscustomobject]@{ Module = 'transactions'; App = 'TRANSACTIONS-SERVICE'; Port = 8085; Jar = 'transactions-*.jar' },
    [pscustomobject]@{ Module = 'audit'; App = 'AUDIT-SERVICE'; Port = 8086; Jar = 'audit-*.jar' },
    [pscustomobject]@{ Module = 'security-service'; App = 'SECURITY-SERVICE'; Port = 8087; Jar = 'security-service-*.jar' },
    [pscustomobject]@{ Module = 'api-gateway-service'; App = 'API-GATEWAY-SERVICE'; Port = 8080; Jar = 'api-gateway-service-*.jar' }
)

function Wait-HttpEndpoint {
    param([string]$Uri, [string]$Name, [Diagnostics.Process]$Process)
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) { throw "$Name exited with code $($Process.ExitCode) before becoming ready." }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) { return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not become ready within $StartupTimeoutSeconds seconds."
}

function Wait-EurekaApplication {
    param([string]$Application, [Diagnostics.Process]$Process)
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    $uri = "http://localhost:8761/eureka/apps/$Application"
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) { throw "$Application exited with code $($Process.ExitCode) before registering in Eureka." }
        try {
            $result = Invoke-RestMethod -Uri $uri -Headers @{ Accept = 'application/json' } -TimeoutSec 3
            $instances = @($result.application.instance)
            if ($instances | Where-Object { $_.status -eq 'UP' -and $_.hostName -eq 'localhost' }) { return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "$Application did not register as UP in Eureka within $StartupTimeoutSeconds seconds."
}

function Wait-TcpPort {
    param([int]$Port, [string]$Name, [Diagnostics.Process]$Process)
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) { throw "$Name exited with code $($Process.ExitCode) before opening port $Port." }
        $client = New-Object Net.Sockets.TcpClient
        try {
            if ($client.ConnectAsync('127.0.0.1', $Port).Wait(1000) -and $client.Connected) { return }
        } catch { } finally {
            $client.Dispose()
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not listen on port $Port within $StartupTimeoutSeconds seconds."
}

function Start-PlatformService {
    param($Service)
    $targetDirectory = Join-Path (Join-Path $Root $Service.Module) 'target'
    $jar = Get-ChildItem -Path $targetDirectory -Filter $Service.Jar -File |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $jar) { throw "No executable JAR found for $($Service.Module). Run scripts/build-platform.ps1 first." }

    $stdout = Join-Path $logDirectory "$($Service.Module).out.log"
    $stderr = Join-Path $logDirectory "$($Service.Module).err.log"
    $quotedJar = "`"$($jar.FullName)`""
    $process = Start-Process -FilePath 'java' -ArgumentList @('-jar', $quotedJar) -WorkingDirectory $Root `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    $script:startedProcesses += [pscustomobject]@{
        Module = $Service.Module
        ProcessId = $process.Id
        Port = $Service.Port
        StartedAt = (Get-Date).ToString('o')
    }
    $script:startedProcesses | ConvertTo-Json | Set-Content -Path $pidFile
    Write-Host "Started $($Service.Module) (PID $($process.Id))."
    return $process
}

New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null

if (Test-Path $pidFile) {
    throw "The platform PID file already exists at $pidFile. Run scripts/stop-all.ps1 before starting again."
}

$requiredVariables = @('DBURL', 'DBUSER', 'DBPASSWORD')
$missingVariables = @($requiredVariables | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) })
if ($missingVariables.Count -gt 0) {
    throw "Set these environment variables before startup: $($missingVariables -join ', ')."
}
if ([string]::IsNullOrWhiteSpace($env:DBUSERNAME)) { $env:DBUSERNAME = $env:DBUSER }
$env:EUREKA_SERVER_URL = 'http://localhost:8761/eureka'
$env:EUREKA_DEFAULT_ZONE = 'http://localhost:8761/eureka'
$env:JWT_ISSUER = 'http://localhost:8087'
$env:JWT_JWK_SET_URI = 'http://localhost:8087/auth/jwks'

# Some shells provide both PATH and Path. Start-Process treats those as duplicate
# keys on Windows, so normalize the inherited process environment before launch.
$inheritedPath = $env:Path
[Environment]::SetEnvironmentVariable('PATH', $null, [EnvironmentVariableTarget]::Process)
[Environment]::SetEnvironmentVariable('Path', $inheritedPath, [EnvironmentVariableTarget]::Process)

$occupiedPorts = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -in $services.Port } |
    Select-Object -ExpandProperty LocalPort -Unique)
if ($occupiedPorts.Count -gt 0) {
    throw "Required ports are already in use: $($occupiedPorts -join ', ')."
}

if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot 'build-platform.ps1') -Root $Root
    if ($LASTEXITCODE -ne 0) { throw 'Platform build failed.' }
}

$script:startedProcesses = @()
try {
    foreach ($service in $services) {
        $process = Start-PlatformService -Service $service
        if ($service.Module -eq 'discovery-service') {
            Wait-HttpEndpoint -Uri 'http://localhost:8761' -Name 'Discovery service' -Process $process
        } else {
            Wait-TcpPort -Port $service.Port -Name $service.Module -Process $process
            Wait-EurekaApplication -Application $service.App -Process $process
        }
    }
    Wait-HttpEndpoint -Uri 'http://localhost:8080/' -Name 'MoneyBags gateway and UI' -Process $process
} catch {
    Write-Warning $_.Exception.Message
    Write-Output 'Startup stopped. Review logs/*.err.log and run scripts/stop-all.ps1 before retrying.'
    exit 1
}

Write-Output 'MoneyBags is ready locally at http://localhost:8080'
Write-Output 'Possible VPN URLs:'
Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' } |
    Sort-Object @{ Expression = { if ($_.InterfaceAlias -match 'VPN') { 0 } else { 1 } } }, InterfaceAlias |
    ForEach-Object { Write-Output "  http://$($_.IPAddress):8080  [$($_.InterfaceAlias)]" }
