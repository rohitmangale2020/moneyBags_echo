# Banking Platform

A Maven multi-module Spring Boot platform containing discovery, security, API
gateway, users, customers, products, accounts, transactions, and audit services.
Business services use Oracle and register with the local Eureka discovery service.

## Security and Gateway

`api-gateway-service` is the public entry point on port `8080`. It forwards
`/auth/**` to `security-service` (port `8087`) and routes protected service APIs
under `/api/users/**`, `/api/accounts/**`, `/api/transactions/**`, and the other
service prefixes. Requests without a valid Bearer token are rejected at the
gateway; every downstream service also validates the same JWT independently.

The security service delegates username/password verification to `users` and
issues RSA-signed access tokens with `ADMIN`, `CUSTOMER`, or `EMPLOYEE` roles.
Passwords are stored as BCrypt hashes. Use `POST /auth/login` to receive an
access token. An `ADMIN` may create users with any role; configure
`BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD` once to create the
initial admin account.

For local development the issuer creates an ephemeral RSA key. For any shared or
production environment, configure `JWT_PRIVATE_KEY` (base64 PKCS#8 DER),
`JWT_PUBLIC_KEY` (base64 X.509 DER), and `JWT_KEY_ID`; this keeps tokens valid
across restarts. Set `JWT_ISSUER` and `JWT_JWK_SET_URI`/service URLs when hosts
or ports differ from the local defaults.

## Run

For a workstation deployment that colleagues can access through the company VPN, follow [VPN_DEPLOYMENT.md](VPN_DEPLOYMENT.md).

Set the required Oracle connection environment variables, then build:

```powershell
.\scripts\build-platform.ps1
```

Start a service, for example:

```powershell
mvn -pl users spring-boot:run
```

For the complete platform with readiness checks, use `scripts/start-all.ps1`.
It starts discovery first, waits for each business service in Eureka, and starts
security and the gateway only after their dependencies are ready.

## Accounts and statements

New accounts receive an immutable, unique 12-digit numeric account number. Existing
legacy account numbers remain valid and are not rewritten during account updates.

The transactions service stores generated transaction descriptions and richer statement
entries (`description`, `withdrawalAmount`, `depositAmount`, and `closingBalance`). Legacy
statement rows keep these added fields null. Search an account's current month by default:

```text
GET /api/statements?accountId=<id>
```

Optional filters are `fromDate`, `toDate`, `entryType`, and `channel`. Supported channels
are `WITHDRAWAL`, `DEPOSIT`, `SELF_TRANSFER`, and `INTERNAL_TRANSFER`.
