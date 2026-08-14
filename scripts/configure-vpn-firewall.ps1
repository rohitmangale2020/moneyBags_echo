param(
    [string]$RemoteAddress = '10.0.0.0/8',
    [string]$RuleName = 'MoneyBags VPN Gateway'
)

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this script from an administrator PowerShell window after receiving company approval.'
}

if (Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue) {
    Write-Output "Firewall rule '$RuleName' already exists. No change was made."
    exit 0
}

New-NetFirewallRule -DisplayName $RuleName -Direction Inbound -Action Allow -Protocol TCP `
    -LocalPort 8080 -Profile Domain,Private -RemoteAddress $RemoteAddress | Out-Null
Write-Output "Allowed approved VPN clients ($RemoteAddress) to reach TCP port 8080 on Domain and Private profiles."
