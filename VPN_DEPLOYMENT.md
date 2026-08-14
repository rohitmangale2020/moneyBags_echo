# MoneyBags VPN deployment

This setup runs every MoneyBags service on one Windows workstation and exposes only the API Gateway and Oracle JET UI on TCP port `8080`.

## Prerequisites

- Java 17, Maven, Node.js, and npm are available on `PATH`.
- Oracle is reachable from the workstation.
- The workstation is connected to the company VPN.
- IT has approved inbound access to TCP `8080` from the company VPN.

Set the database variables in the PowerShell session that will start MoneyBags:

```powershell
$env:DBURL = 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:DBUSER = 'your_schema'
$env:DBPASSWORD = 'your_password'
```

Install UI dependencies once:

```powershell
Set-Location UI\BankingPortal
npm install
Set-Location ..\..
```

## Firewall setup

From an administrator PowerShell window, after receiving company approval:

```powershell
.\scripts\configure-vpn-firewall.ps1
```

The default rule allows TCP `8080` from the private `10.0.0.0/8` range on Domain and Private Windows network profiles. If your company uses another VPN range or classifies its VPN as Public, ask IT for the approved `RemoteAddress` and profile before modifying the rule.

## Build and start

Run from the repository root:

```powershell
.\scripts\start-all.ps1
```

The script builds the UI and service JARs, verifies that the UI is inside the gateway JAR, and starts services in dependency order. It waits for each service to register as `UP` in Eureka before continuing.

For a faster restart after an unchanged successful build:

```powershell
.\scripts\start-all.ps1 -SkipBuild
```

Check status and display candidate VPN URLs:

```powershell
.\scripts\health-check.ps1
```

A colleague connected to the company VPN can then open the VPN-adapter URL reported by the script, for example:

```text
http://10.122.198.79:8080
```

The actual address can change when the VPN reconnects. The gateway is the only remotely reachable service; Eureka and ports `8081` through `8090` bind to loopback.

## Stop

```powershell
.\scripts\stop-all.ps1
```

The stop script terminates only the Java process IDs recorded by the MoneyBags launcher.

## Troubleshooting

- If startup reports an occupied port, stop the previous platform instance before retrying.
- If a service does not become `UP`, inspect `logs/<service>.err.log` and `logs/<service>.out.log`.
- If the UI works locally but not from another VPN device, verify the Windows network profile, firewall rule, company VPN client-isolation policy, and the current VPN IP.
- Keep the Eureka dashboard local at `http://localhost:8761`; do not add a firewall rule for it.
