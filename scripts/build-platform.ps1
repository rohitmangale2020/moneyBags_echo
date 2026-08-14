param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$uiDirectory = Join-Path $Root 'UI\BankingPortal'

if (-not (Test-Path (Join-Path $uiDirectory 'node_modules'))) {
    throw "UI dependencies are missing. Run 'npm install' in $uiDirectory first."
}

Push-Location $uiDirectory
try {
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Oracle JET UI build failed.' }
} finally {
    Pop-Location
}

Push-Location $Root
try {
    & mvn.cmd package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
} finally {
    Pop-Location
}

$gatewayJar = Get-ChildItem (Join-Path $Root 'api-gateway-service\target') -Filter 'api-gateway-service-*.jar' -File |
    Where-Object { $_.Name -notlike '*.original' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $gatewayJar) { throw 'The API Gateway JAR was not created.' }

$jarEntries = & jar.exe tf $gatewayJar.FullName
if ($LASTEXITCODE -ne 0 -or $jarEntries -notcontains 'BOOT-INF/classes/static/index.html') {
    throw 'The API Gateway JAR does not contain the Oracle JET index page.'
}
Write-Output "Built platform and verified UI in $($gatewayJar.FullName)."
