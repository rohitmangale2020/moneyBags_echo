<#
Starts and tests the local transaction-risk scoring service.

Examples:
  .\run_model_service.ps1 -Action Start
  .\run_model_service.ps1 -Action Health
  .\run_model_service.ps1 -Action SampleScore
#>

[CmdletBinding()]
param(
    [ValidateSet('Start', 'Health', 'SampleScore')]
    [string]$Action = 'Start',

    [ValidateRange(1, 65535)]
    [int]$Port = 8001,

    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'

$serviceDir = $PSScriptRoot
$venvDir = Join-Path $serviceDir '.venv'
$venvPython = Join-Path $venvDir 'Scripts\python.exe'
$baseUrl = "http://127.0.0.1:$Port"

function Get-PythonCommand {
    if (Test-Path -LiteralPath $venvPython) {
        return $venvPython
    }

    $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
    if ($null -ne $pythonCommand) {
        return $pythonCommand.Source
    }

    $launcherCommand = Get-Command py -ErrorAction SilentlyContinue
    if ($null -ne $launcherCommand) {
        return $launcherCommand.Source
    }

    throw 'Python was not found. Install Python 3.10+ and make sure the python command is available in PowerShell.'
}

function Test-Health {
    try {
        $health = Invoke-RestMethod -Uri "$baseUrl/health" -Method Get -TimeoutSec 5
        $health | ConvertTo-Json -Depth 5
        if ($health.status -ne 'ok') {
            throw "Model service responded with status '$($health.status)'. Run the notebook model sections so model_artifacts contains the saved files."
        }
    }
    catch {
        throw "Cannot reach the model service at $baseUrl. Start it first with: .\run_model_service.ps1 -Action Start`n$($_.Exception.Message)"
    }
}

if ($Action -eq 'Health') {
    Test-Health
    exit 0
}

if ($Action -eq 'SampleScore') {
    Test-Health

    $sampleTransaction = @{
        transaction_ref = 'MODEL-TEST-001'
        transaction_type = 'TRANSFER'
        amount = 60000.00
        old_balance_org = 90000.00
        old_balance_dest = 12000.00
        recipient_prior_tx_count = 0
        recipient_prior_amount = 0.00
        occurred_at = '2026-08-16T03:30:00Z'
    } | ConvertTo-Json

    $score = Invoke-RestMethod -Uri "$baseUrl/score" -Method Post `
        -ContentType 'application/json' -Body $sampleTransaction -TimeoutSec 15
    $score | ConvertTo-Json -Depth 8
    exit 0
}

# Start action
$python = Get-PythonCommand
Set-Location $serviceDir

if (-not (Test-Path -LiteralPath $venvPython)) {
    Write-Host 'Creating an isolated Python environment for the model service...'
    & $python -m venv $venvDir
}

if (-not $SkipInstall) {
    Write-Host 'Installing model-service dependencies...'
    & $venvPython -m pip install --upgrade pip
    & $venvPython -m pip install -r (Join-Path $serviceDir 'requirements.txt')
}

Write-Host "Starting model service at $baseUrl"
Write-Host 'Keep this window open. Open a second PowerShell window to run Health or SampleScore.'
& $venvPython -m uvicorn app:app --host 127.0.0.1 --port $Port
