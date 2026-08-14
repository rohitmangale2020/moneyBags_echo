$expectedApplications = @(
    'USERS-SERVICE',
    'CUSTOMERS-SERVICE',
    'PRODUCTS-SERVICE',
    'ACCOUNTS-SERVICE',
    'TRANSACTIONS-SERVICE',
    'AUDIT-SERVICE',
    'SECURITY-SERVICE',
    'API-GATEWAY-SERVICE'
)

try {
    $registry = Invoke-RestMethod -Uri 'http://localhost:8761/eureka/apps' -Headers @{ Accept = 'application/json' } -TimeoutSec 5
    foreach ($applicationName in $expectedApplications) {
        $application = @($registry.applications.application) | Where-Object { $_.name -eq $applicationName }
        $up = @($application.instance) | Where-Object { $_.status -eq 'UP' }
        if ($up) { Write-Output "UP   $applicationName" } else { Write-Output "DOWN $applicationName" }
    }
} catch {
    Write-Output 'DOWN DISCOVERY-SERVICE'
}

try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/' -TimeoutSec 5
    Write-Output "UP   MONEYBAGS-UI ($($response.StatusCode))"
} catch {
    Write-Output 'DOWN MONEYBAGS-UI'
}

Write-Output 'Possible VPN URLs:'
Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' } |
    ForEach-Object { Write-Output "  http://$($_.IPAddress):8080  [$($_.InterfaceAlias)]" }
