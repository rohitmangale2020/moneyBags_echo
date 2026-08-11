param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$services = @('eureka-server', 'users', 'customers', 'products', 'accounts', 'transactions', 'audit', 'security-service', 'api-gateway-service')
$logDirectory = Join-Path $Root 'logs'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null

foreach ($service in $services) {
    $log = Join-Path $logDirectory "$service.log"
    Start-Process -FilePath 'mvn' -ArgumentList "-pl $service spring-boot:run" -WorkingDirectory $Root -RedirectStandardOutput $log -RedirectStandardError $log -WindowStyle Hidden
    if ($service -eq 'eureka-server') { Start-Sleep -Seconds 10 } else { Start-Sleep -Seconds 3 }
}

Write-Output 'Services were started. Run scripts/health-check.ps1 to verify them.'
