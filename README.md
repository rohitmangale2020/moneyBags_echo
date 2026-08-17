# Banking Platform

A Maven multi-module Spring Boot starter containing six independent services:
`users`, `customers`, `products`, `accounts`, `transactions`, and `audit`.

Every service includes Spring Web, Spring Web Services, Spring Security, MySQL
Driver, Spring Cloud Gateway, and Resilience4j. Eureka is intentionally not
configured yet; it can be added later as a separate infrastructure service.

## Security and Gateway

`api-gateway-service` is the public entry point on port `8080`. It forwards
`/auth/**` to `security-service` (port `8087`) and routes protected service APIs
under `/api/users/**`, `/api/accounts/**`, `/api/transactions/**`, and the other
service prefixes. Requests without a valid Bearer token are rejected at the
gateway; every downstream service also validates the same JWT independently.

The security service delegates username/password verification to `users` and
issues 15-minute RSA-signed access tokens with `ADMIN`, `CUSTOMER`, or
`EMPLOYEE` roles. Passwords are stored as BCrypt hashes. Use
`POST /auth/register` to create a `CUSTOMER`, then `POST /auth/login` to receive
an access token. The users service exposes authenticated `GET /api/users/me`.
An `ADMIN` may create accounts with any role through `POST /api/users`; configure
`BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD` once to create the
initial admin account.

For local development the issuer creates an ephemeral RSA key. For any shared or
production environment, configure `JWT_PRIVATE_KEY` (base64 PKCS#8 DER),
`JWT_PUBLIC_KEY` (base64 X.509 DER), and `JWT_KEY_ID`; this keeps tokens valid
across restarts. Set `JWT_ISSUER` and `JWT_JWK_SET_URI`/service URLs when hosts
or ports differ from the local defaults.

## Run

Copy `.env.example` to `.env`, then enter the Oracle database connection values
for your local environment. The launcher reads this file automatically:

```powershell
Copy-Item .env.example .env
```

Then build:

```powershell
mvn clean verify
```

Start all backend services and the UI with one command:

```powershell
.\scripts\start-all.ps1
```

Or double-click `start-all.cmd` from Windows Explorer. The UI is available at
`http://localhost:8000`, while the API gateway is on port `8080`. Logs are
written to `logs/`. To start only the backend services, use
`.\scripts\start-all.ps1 -SkipUi`.

Start a single service, for example:

```powershell
mvn -pl users spring-boot:run
```

Start the services in this order: `discovery-service`, `audit`, the business
services (`users`, `customers`, `products`, `accounts`, `transactions`),
`security-service`, and finally `api-gateway-service`.

For audit persistence, start `audit` before the business services. The local
default audit URL is `http://localhost:8086`; override it with
`SERVICES_AUDIT_BASE_URL` when needed. All services and the audit service must
share the same `AUDIT_INTERNAL_KEY`. The audit service creates/updates its seven
audit tables through Hibernate when it starts against Oracle.

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

## Ledger

The transactions service has a small immutable double-entry ledger. It uses only
`ledger_account` and `ledger_entry`; a journal header is deliberately not stored.
The entries sharing a `transactionRef` are the ledger posting and must balance.
Completed deposits and withdrawals produce two entries, while internal transfers
produce four entries through the `INTERNAL_CLEARING` account.

The service bootstraps `CASH_ON_HAND`, `CUSTOMER_DEPOSITS`, and
`INTERNAL_CLEARING`. Ledger APIs are available through the gateway:

```text
GET  /api/ledger/accounts
POST /api/ledger/accounts
GET  /api/ledger/entries?transactionRef=<reference>
GET  /api/ledger/entries?accountCode=<code>
POST /api/ledger/entries
```

`transactions/src/main/resources/db/ledger-schema-oracle.sql` is the reference
Oracle DDL for managed environments. Local development creates these tables using
the existing Hibernate `ddl-auto=update` setting.

## Deposit products

Accounts snapshot the selected product's type, minimum balance, annual rate, FD tenure
and maturity rules when the account is opened. Maximum-balance limits are not used;
legacy maximum-balance columns and API fields remain null for database/API compatibility.
Savings and salary accounts receive monthly interest; current accounts do not receive
interest. Ordinary customer withdrawals must preserve the configured minimum balance.
Existing accounts can be backfilled once by an admin:

```text
POST /api/accounts/product-rules/refresh
```

Monthly interest is processed at 01:15 on the first day of the month. The basic
calculation uses the account balance at processing time, the snapshotted annual rate,
and actual elapsed days over a 365-day year. Every non-zero payout creates a completed
bank transaction, account statement, ledger posting and outbox event. Run a period
manually as an admin with:

```text
POST /api/deposit-processing/interest?asOf=2026-08-31
```

Every account is persisted with a zero balance. A requested opening amount for a savings,
salary or current account is then posted through an idempotent `OPENING_DEPOSIT`
transaction, producing the account balance change, statement, ledger and outbox records.
This avoids creating money by writing an account balance directly.

An FD account must first be opened with zero balance using an active `FD` product.
Fund it from the customer's savings/current/salary account. The funding account is also
the mandatory payout account, so principal and interest always return to the account
from which the FD was funded:

```text
POST /api/fixed-deposits
{
  "fdAccountId": "<empty FD account>",
  "fundingAccountId": "<source account>",
  "payoutAccountId": "<same source account>",
  "principal": 10000.00
}
```

The funding transfer is idempotent and creates its normal transaction, statement,
ledger and outbox rows. A daily job pays principal and accrued interest to the original
funding account on maturity. There is no lock-in: an active FD can be withdrawn at any
time with `POST /api/fixed-deposits/{contractId}/close`. The default premature-withdrawal
penalty reduces the applicable annual rate by one percentage point, so principal is
protected but the interest payout is lower.

A transactional account linked to an active FD cannot be closed. Withdraw the FD first,
move the returned balance out of the account, and then close the now-zero-balance account.
Directly closing an FD account is also rejected because it would bypass its payout flow.

Annual maintenance fees configured on active savings and current products are processed
by a daily 02:00 anniversary scan. Each account/year uses one deterministic transaction
reference, creates a debit statement, posts fee income to the ledger and emits the normal
outbox event. A missed anniversary is caught up by a later scan, while a completed yearly
fee is skipped. A fee may take the balance below the product minimum but never below zero.
Run the scan manually as admin:

```text
POST /api/deposit-processing/annual-fees?asOf=2026-08-17
```

Scheduler timings, local service URLs, the shared internal service key and the premature
rate penalty are configurable through the variables documented in `.env.example`. The
physical `PRODUCT_FEE.MONTHLY_MAINTENANCE_FEE` column is deliberately reused as the
annual fee value to avoid changing the existing database schema; review old product fee
amounts once because values previously entered as monthly amounts now mean annual amounts.
