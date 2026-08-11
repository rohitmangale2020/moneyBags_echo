$urls = @(
    'http://localhost:8761',
    'http://localhost:8080/actuator/health',
    'http://localhost:8081/actuator/health',
    'http://localhost:8082/actuator/health',
    'http://localhost:8083/actuator/health',
    'http://localhost:8084/actuator/health',
    'http://localhost:8085/actuator/health',
    'http://localhost:8086/actuator/health',
    'http://localhost:8087/actuator/health'
)

foreach ($url in $urls) {
    try { Write-Output "UP   $url  $((Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5).StatusCode)" }
    catch { Write-Output "DOWN $url" }
}
