param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$pidFile = Join-Path (Join-Path $Root 'logs') 'platform-processes.json'
if (-not (Test-Path $pidFile)) {
    Write-Output 'No MoneyBags PID file was found. Nothing was stopped.'
    exit 0
}

$processes = @(Get-Content $pidFile -Raw | ConvertFrom-Json)
foreach ($entry in $processes) {
    $process = Get-Process -Id $entry.ProcessId -ErrorAction SilentlyContinue
    if ($process) {
        $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($entry.ProcessId)" -ErrorAction SilentlyContinue
        if ($details.CommandLine -like "*$($entry.Module)*") {
            Stop-Process -Id $entry.ProcessId
            Write-Output "Stopped $($entry.Module) (PID $($entry.ProcessId))."
        } else {
            Write-Warning "PID $($entry.ProcessId) no longer belongs to $($entry.Module); it was not stopped."
        }
    }
}
Remove-Item -LiteralPath $pidFile -Force
