# MoneyBags API catalog

Source of truth: Spring MVC controller and request DTO source in this repository, reviewed on 2026-08-18.

## Using the catalog

- **Gateway base URL:** `http://localhost:8080`. Paths marked **Gateway** are available through it.
- **Direct service URLs:** Security `:8087`, Users `:8081`, Customers `:8082`, Accounts `:8084`, Transactions `:8085`, Audit `:8086`, Products `:8090`.
- Every **Endpoint path** in the gateway sections is appended to `http://localhost:8080`; for example, `POST /auth/login` means `POST http://localhost:8080/auth/login`.
- Unless a route is marked **Public**, gateway requests require `Authorization: Bearer <accessToken>`.
- `*` marks a required JSON field or request parameter. Dates use `YYYY-MM-DD`; date-times use ISO-8601 `YYYY-MM-DDTHH:mm:ss`.
- The gateway forwards only the routes configured in `api-gateway-service/application.yml`. **Internal** routes below are implemented for service-to-service calls but are not forwarded by the gateway.
- Standard paged responses are Spring `Page` JSON objects. `page` is zero-based; the code clamps `page >= 0` and `1 <= size <= 100` where it performs manual pagination.

### Gateway route ownership

| Endpoint path prefix | Owning service | Direct base URL |
|---|---|---|
| `/auth/**`, `/.well-known/**` | security-service | `http://localhost:8087` |
| `/api/v1/users/**` | users-service | `http://localhost:8081` |
| `/api/customers/**` | customers-service | `http://localhost:8082` |
| `/api/v1/products/**`, `/api/v1/product-types/**` | products-service | `http://localhost:8090` |
| `/api/accounts/**` | accounts-service | `http://localhost:8084` |
| `/api/transactions/**`, `/api/statements/**`, `/api/ledger/**`, `/api/fixed-deposits/**`, `/api/deposit-processing/**` | transactions-service | `http://localhost:8085` |
| `/api/audit/**` | audit-service | `http://localhost:8086` |

## Request body schemas

Use these schema names in the endpoint tables below. Fields in parentheses are optional. Constraints are taken from Bean Validation annotations.

### Authentication and AI

| Schema | JSON fields |
|---|---|
| `Credentials` | `username*` (non-blank), `password*` (non-blank) |
| `AssistantRequest` | `message*` (non-blank), (`customerId`: integer), (`transactionId`: string), (`accountId`: string), (`module`: string). The current GPT-OSS controller forwards only `message` to the model. |

### Users

| Schema | JSON fields |
|---|---|
| `UserProfileRequest` | `firstName*` (max 80), (`middleName`, max 80), `lastName*` (max 80), (`phoneNumber`, international E.164-like `+?[1-9]\d{7,14}`), (`dateOfBirth`, past date), (`addressLine1`, `addressLine2`, max 150), (`city`, `state`, max 80), (`postalCode`, max 20), (`countryCode`, two letters) |
| `CreateUserRequest` | `username*` (3–50), `email*` (valid email, max 254), `password*` (8–72), `role*` (max 50), `profile*` (`UserProfileRequest`) |
| `UpdateUserRequest` | `username*` (3–50), `email*` (valid email), `role*`, `profile*` (`UserProfileRequest`) |
| `UpdatePasswordRequest` | `password*` (8–72) |
| `UpdateUserStatusRequest` | `status*` (`UserStatus`) |

### Customers

| Schema | JSON fields |
|---|---|
| `CustomerRequest` | `firstName*` (max 100), (`lastName`, max 100), `dob*` (past), `gender*` (`MALE`, `FEMALE`, `OTHER`), `phone*` (Indian mobile: `[6-9][0-9]{9}`), (`email`, valid/max 254), (`occupation`, max 100) |
| `AddressRequest` | `addressType*` (`CURRENT`, `PERMANENT`, `OFFICE`), `line1*` (max 250), (`line2`, max 250), `city*`, `state*`, `country*` (each max 100), `pincode*` (six-digit Indian PIN, non-zero first digit) |
| `KycRequest` | `kycStatus*` (`KycStatusType`), (`kycDate`, today or earlier), (`verifiedBy`, max 100), (`riskLevel`), (`riskScore`, 0–100), (`expiryDate`), (`remarks`, max 500), (`updatedBy`, max 100) |
| `DocumentRequest` | `documentType*` (`DocumentType`), (`documentNumber`, max 100), (`issueDate`, today or earlier), (`expiryDate`), (`status`: `DocumentStatusType`), (`verifiedBy`, max 100), (`rejectedReason`, max 500), (`remarks`, max 500), (`updatedBy`, max 100) |
| `NomineeRequestDto` | `nomineeName*` (max 150), (`relationship`, max 100), `relationType*` (`NOMINEE`, `JOIN_HOLDER`, `GUARDIAN`, `AUTHORIZED_SIGNATORY`), (`dob`, past), (`phone`, blank or Indian mobile), (`address`: `AddressRequest`), (`sharePercentage`, 0.01–100), (`status`: `ACTIVE`, `INACTIVE`, `PENDING`, `CLOSED`), (`updatedBy`, max 100), (`startDate`, `endDate`) |

### Products

| Schema | JSON fields |
|---|---|
| `RateRequest` | `interestRate*` (decimal >= 0) |
| `FeeRequest` | `annualMaintenanceFee*` (decimal >= 0) |
| `TermRequest` | (`tenureMonths`), (`installmentAmount`, decimal >= 0), (`installmentFrequency`), (`lockInPeriod`), (`maturityInstruction`), (`prematureWithdrawalAllowed`) |
| `ProductRequest` | `productCode*` (max 50), `productName*` (max 150), `productTypeCode*` (max 30), (`description`, max 500), (`minimumBalance`, >= 0), (`maximumBalance`, >= 0), `currency*` (three uppercase letters), `status*` (`ACTIVE` or `RETIRED`), `rate*` (`RateRequest`), `term*` (`TermRequest`), `fee*` (`FeeRequest`) |
| `ProductTypeRequest` | `productTypeCode*` (max 30), `productTypeName*` (max 100), (`description`, max 500), `status*` (`ACTIVE` or `RETIRED`) |
| `StatusRequest` | `status*` (currently only `RETIRED`), `reason*` (max 500) |
| `RetirementRequest` | (`migrationProductCode`) |

### Accounts and transactions

| Schema | JSON fields |
|---|---|
| `AccountRequest` | (`accountNumber`, max 24), `customerId*`, `productId*`, `ownershipType*` (`OwnershipType`), `status*` (`AccountStatus`), `currencyCode*` (three letters), `availableBalance*` (>= 0), (`closedAt`: date-time) |
| `AccountTransferRequest` | `transactionRef*` (max 40), `debitAccountId*` (max 36), `creditAccountId*` (max 36), `amount*` (>= 0.0001), `currencyCode*` (three letters), (`customerId`, max 36), (`purpose`; defaults to `STANDARD`) |
| `AccountAdjustmentRequest` | `transactionRef*` (max 40), `adjustmentType*` (`OPENING_DEPOSIT`, `DEPOSIT`, `WITHDRAWAL`, `MONTHLY_MAINTENANCE_FEE`, `ANNUAL_MAINTENANCE_FEE`, `INTEREST_CREDIT`, `FIXED_DEPOSIT_INTEREST_CREDIT`), `amount*` (>= 0.0001), `currencyCode*` (three letters), (`effectiveDate`) |
| `InterestProcessingRequest` | `periodEnd*` (date), `transactionRef*` |
| `TransactionRequest` | `transactionRef*` (max 40), `transactionType*`, `transactionStatus*`, (`debitAccountId`), (`creditAccountId`), (`externalBeneficiary`), `amount*` (>= 0.01), `currencyCode*` (three letters), (`feeAmount`, >= 0), (`initiatedByCustomerId`), (`initiatedByUserId`; server replaces it with JWT user), (`completedAt`), (`failureCode`), (`failureReason`). Create permits only `TRANSFER`, `OPENING_DEPOSIT`, `DEPOSIT`, and `WITHDRAWAL`. |
| `StatementRequest` | `transactionId*`, `accountId*`, (`description`, max 500), `entryType*` (`StatementEntryType`), `amount*` (>= 0.01), `currencyCode*` (three letters), `balanceAfter*` |
| `LedgerAccountRequest` | `code*` (2–60; letters, digits, `_`, `-`), `name*` (max 160), `accountType*` (`LedgerAccountType`) |
| `LedgerPostingRequest` | `transactionRef*` (max 40), (`postingDate`), `currencyCode*` (three letters), (`description`, max 500), `items*` (non-empty array). Each item: `ledgerAccountCode*` (max 60), (`customerAccountId`, max 36), `entryType*` (`LedgerEntryType`), `amount*` (>= 0.0001), (`description`, max 500). |
| `FixedDepositOpenRequest` | `fdAccountId*`, `fundingAccountId*`, `payoutAccountId*`, `principal*` (>= 0.01) |

### Audit event bodies

All audit `POST` endpoints take an entity-shaped JSON object. The shared fields are `auditId`, `correlationId`, `action`, `actorId`, `actorType` (`USER`, `CUSTOMER`, `SERVICE`, `SYSTEM`, `ANONYMOUS`), `outcome` (`SUCCESS`, `FAILED`, `REJECTED`), `description`, `errorCode`, `errorMessage`, `changedFields`, `oldValuesJson`, `newValuesJson`, and `createdAt`. `auditId` and `createdAt` are generated if omitted.

| Endpoint category | Additional body fields |
|---|---|
| users | `targetUserId`, `previousStatus`, `newStatus`, `previousRole`, `newRole` |
| customers | `customerId`, `relatedEntityType`, `relatedEntityId`, `previousStatus`, `newStatus` |
| products | `productId`, `componentType`, `componentId`, `previousStatus`, `newStatus`, `changeSummary` |
| accounts | `accountId`, `customerId`, `transactionId`, `transactionRef`, `operationId`, `previousStatus`, `newStatus`, `amount`, `currencyCode`, `balanceBefore`, `balanceAfter`, `reason` |
| transactions | `transactionId`, `transactionRef`, `debitAccountId`, `creditAccountId`, `previousStatus`, `newStatus`, `amount`, `currencyCode`, `relatedEntityType`, `relatedEntityId`, `failureReason` |
| security | `userId`, `username`, `clientIp`, `userAgent` |
| api-access | `username`, `targetService`, `httpMethod`, `requestPath`, `httpStatus`, `clientIp`, `durationMs` |

## Gateway APIs

### Security service — public

| Method | Path | Parameters / body | Notes |
|---|---|---|---|
| `POST` | `/auth/login` | body: `Credentials` | Returns JWT access token. Internally authenticates against users-service. |
| `GET` | `/auth/jwks` | none | RSA public JWK set. |
| `GET` | `/.well-known/openid-configuration` | none | Returns issuer and `jwks_uri`. |
| `POST` | `/auth/gpt-oss/chat` | body: `AssistantRequest` | Read-only AI guidance; gateway treats all `/auth/**` as public. |

### Users service

| Method | Path | Parameters / body |
|---|---|---|
| `POST` | `/api/v1/users` | body: `CreateUserRequest` |
| `GET` | `/api/v1/users/{id}` | path: `id*` (long) |
| `GET` | `/api/v1/users` | query: (`q`), (`page`, default 0), (`size`, default 20), (`sort`, default `createdAt,DESC`) |
| `PUT` | `/api/v1/users/{id}` | path: `id*`; body: `UpdateUserRequest` |
| `PATCH` | `/api/v1/users/{id}/password` | path: `id*`; body: `UpdatePasswordRequest` |
| `PATCH` | `/api/v1/users/{id}/status` | path: `id*`; body: `UpdateUserStatusRequest` |
| `DELETE` | `/api/v1/users/{id}` | path: `id*`; deactivates user |

### Customers service

| Method | Path | Parameters / body |
|---|---|---|
| `POST` | `/api/customers` | body: `CustomerRequest` |
| `GET` | `/api/customers/{customerId}` | path: `customerId*` (long) |
| `GET` | `/api/customers` | query: (`status`: `CustomerStatus`), (`page`, default 0), (`size`, default 10), (`sort`, default `status,createdAt`) |
| `PUT` | `/api/customers/{customerId}` | path: `customerId*`; body: `CustomerRequest` |
| `DELETE` | `/api/customers/{customerId}` | path: `customerId*` |
| `PATCH` | `/api/customers/{customerId}/activate` | path: `customerId*` |
| `PATCH` | `/api/customers/{customerId}/deactivate` | path: `customerId*` |
| `GET` | `/api/customers/search/cif/{cifNo}` | path: `cifNo*` |
| `GET` | `/api/customers/search/email/{email}` | path: `email*` (URL-encode it) |
| `GET` | `/api/customers/search/phone/{phone}` | path: `phone*` |
| `GET` | `/api/customers/search/first-name/{firstName}` | path: `firstName*` |
| `GET` | `/api/customers/status/{status}` | path: `status*` (`CustomerStatus`) |
| `POST` | `/api/customers/{customerId}/addresses` | path: `customerId*`; body: `AddressRequest` |
| `GET` | `/api/customers/{customerId}/addresses` | path: `customerId*` |
| `GET` | `/api/customers/{customerId}/addresses/{addressId}` | path: `customerId*`, `addressId*` (long) |
| `PUT` | `/api/customers/{customerId}/addresses/{addressId}` | path: `customerId*`, `addressId*`; body: `AddressRequest` |
| `DELETE` | `/api/customers/{customerId}/addresses/{addressId}` | path: `customerId*`, `addressId*` |
| `POST` | `/api/customers/{customerId}/kyc` | path: `customerId*`; body: `KycRequest` |
| `GET` | `/api/customers/{customerId}/kyc` | path: `customerId*` |
| `PUT` | `/api/customers/{customerId}/kyc` | path: `customerId*`; body: `KycRequest` |
| `POST` | `/api/customers/{customerId}/documents` | path: `customerId*`; `multipart/form-data`: `file*` (`MultipartFile`), `data*` (JSON bytes for `DocumentRequest`) |
| `GET` | `/api/customers/{customerId}/documents` | path: `customerId*` |
| `GET` | `/api/customers/{customerId}/documents/{docId}` | path: `customerId*`, `docId*` (long) |
| `PUT` | `/api/customers/{customerId}/documents/{docId}` | path: `customerId*`, `docId*`; `multipart/form-data`: (`file`), `data*` (`DocumentRequest` JSON part) |
| `POST` | `/api/customers/{customerId}/nominees` | path: `customerId*`; body: `NomineeRequestDto` |
| `GET` | `/api/customers/{customerId}/nominees` | path: `customerId*` |
| `GET` | `/api/customers/{customerId}/nominees/{nomineeId}` | path: `customerId*`, `nomineeId*` (long) |
| `PUT` | `/api/customers/{customerId}/nominees/{nomineeId}` | path: `customerId*`, `nomineeId*`; body: `NomineeRequestDto` |
| `PATCH` | `/api/customers/{customerId}/nominees/{nomineeId}/close` | path: `customerId*`, `nomineeId*` |
| `DELETE` | `/api/customers/{customerId}/nominees/{nomineeId}` | path: `customerId*`, `nomineeId*` |

### Products service

| Method | Path | Parameters / body | Role restriction |
|---|---|---|---|
| `GET` | `/api/v1/products` | none | authenticated |
| `POST` | `/api/v1/products` | body: `ProductRequest` | `ADMIN` |
| `GET` | `/api/v1/products/id/{productId}` | path: `productId*` (long) | authenticated |
| `GET` | `/api/v1/products/{productCode}` | path: `productCode*` | authenticated |
| `GET` | `/api/v1/products/{productCode}/status-history` | path: `productCode*` | authenticated |
| `GET` | `/api/v1/products/{productCode}/retirement-impact` | path: `productCode*` | `ADMIN` |
| `PUT` | `/api/v1/products/{productCode}` | path: `productCode*`; body: `ProductRequest` | `ADMIN` |
| `PATCH` | `/api/v1/products/{productCode}/status` | path: `productCode*`; body: `StatusRequest` | `EMPLOYEE` |
| `POST` | `/api/v1/products/{productCode}/retire` | path: `productCode*`; optional body: `RetirementRequest` | `ADMIN` |
| `DELETE` | `/api/v1/products/{productCode}` | path: `productCode*` | `ADMIN`; legacy retirement |
| `GET` | `/api/v1/product-types` | none | authenticated |
| `POST` | `/api/v1/product-types` | body: `ProductTypeRequest` | `ADMIN` |

### Accounts service

| Method | Path | Parameters / body | Notes |
|---|---|---|---|
| `GET` | `/api/accounts/{accountId}` | path: `accountId*` | |
| `GET` | `/api/accounts` | query: (`customerId`), (`accountNumber`), (`status`: `AccountStatus`), (`ownershipType`: `OwnershipType`), (`currencyCode`), (`page`), (`size`) | `accountNumber` returns one-item list; filtering/pagination changes result type to `Page`. |
| `POST` | `/api/accounts` | body: `AccountRequest` | authenticated JWT user recorded as creator |
| `PUT` | `/api/accounts/{accountId}` | path: `accountId*`; body: `AccountRequest` | authenticated JWT user recorded as updater |
| `POST` | `/api/accounts/transfers` | body: `AccountTransferRequest` | `STANDARD` purpose allowed normally; non-standard requires `SYSTEM` or `ADMIN`. |
| `POST` | `/api/accounts/product-rules/refresh` | none | `ADMIN`; returns `updatedAccounts` count. |
| `POST` | `/api/accounts/{accountId}/adjustments` | path: `accountId*`; body: `AccountAdjustmentRequest` | opening deposit/deposit/withdrawal allowed normally; other adjustments require `SYSTEM` or `ADMIN`. |

### Transactions service

| Method | Path | Parameters / body | Notes |
|---|---|---|---|
| `GET` | `/api/transactions/{transactionId}` | path: `transactionId*` | |
| `GET` | `/api/transactions` | query: (`transactionRef`), (`debitAccountId`), (`creditAccountId`), (`page`), (`size`) | Priority: ref, debit account, credit account, pagination, all. |
| `POST` | `/api/transactions` | body: `TransactionRequest` | Create returns `201`, or `422` if created transaction is `FAILED`. |
| `PUT` | `/api/transactions/{transactionId}` | path: `transactionId*`; body: `TransactionRequest` | |
| `GET` | `/api/statements/{statementId}` | path: `statementId*` | |
| `GET` | `/api/statements` | query: `accountId*`, (`fromDate`), (`toDate`), (`entryType`: `StatementEntryType`), (`channel`: `TransactionChannel`) | |
| `GET` | `/api/statements/monthly` | query: `accountId*`, `year*` (integer), `month*` (integer) | |
| `POST` | `/api/statements` | body: `StatementRequest` | |
| `GET` | `/api/ledger/accounts` | none | |
| `GET` | `/api/ledger/accounts/{code}` | path: `code*` | |
| `POST` | `/api/ledger/accounts` | body: `LedgerAccountRequest` | |
| `GET` | `/api/ledger/entries` | query: (`transactionRef`), (`accountCode`) | Either or neither may be supplied. |
| `POST` | `/api/ledger/entries` | body: `LedgerPostingRequest` | Ledger service validates/postings must balance. |
| `GET` | `/api/fixed-deposits` | none | |
| `GET` | `/api/fixed-deposits/{contractId}` | path: `contractId*` | |
| `POST` | `/api/fixed-deposits` | body: `FixedDepositOpenRequest` | Returns `201`. |
| `POST` | `/api/fixed-deposits/{contractId}/retry-funding` | path: `contractId*` | |
| `POST` | `/api/fixed-deposits/{contractId}/close` | path: `contractId*`; query: (`asOf`, date; defaults to server today) | Premature close workflow. |
| `POST` | `/api/deposit-processing/interest` | query: `asOf*` (date) | `ADMIN` |
| `POST` | `/api/deposit-processing/maturities` | query: `asOf*` (date) | `ADMIN` |
| `POST` | `/api/deposit-processing/annual-fees` | query: `asOf*` (date) | `ADMIN` |

### Audit service

The gateway exposes all the following routes under `/api/audit/**`. For each category, the body fields are specified in [Audit event bodies](#audit-event-bodies). List endpoints return a page ordered by newest `createdAt` first.

| Category / service | Create | Get one | List query parameters |
|---|---|---|---|
| users | `POST /api/audit/users` | `GET /api/audit/users/{auditId}` | (`targetUserId`) **or** one common filter: (`correlationId`), (`action`), (`outcome`: `SUCCESS\|FAILED\|REJECTED`), or (`from` **and** `to` date-time); (`page`, default 0), (`size`, default 20) |
| customers | `POST /api/audit/customers` | `GET /api/audit/customers/{auditId}` | (`customerId`) **or** one common filter above; pagination |
| products | `POST /api/audit/products` | `GET /api/audit/products/{auditId}` | (`productId`) **or** one common filter above; pagination |
| accounts | `POST /api/audit/accounts` | `GET /api/audit/accounts/{auditId}` | exactly one of (`accountId`, `transactionId`, `transactionRef`) **or** one common filter; pagination |
| transactions | `POST /api/audit/transactions` | `GET /api/audit/transactions/{auditId}` | exactly one of (`transactionId`, `transactionRef`) **or** one common filter; pagination |
| security | `POST /api/audit/security` | `GET /api/audit/security/{auditId}` | (`userId`) **or** one common filter; pagination |
| api access | `POST /api/audit/api-access` | `GET /api/audit/api-access/{auditId}` | exactly one of (`targetService`, `httpStatus`) **or** one common filter; pagination |

For all audit list routes, common/specific filters cannot be combined. `from` and `to` must be supplied together. Page size is clamped to 1–100.

## Internal service-to-service APIs

These controller endpoints are not matched by the gateway route configuration. They are intended for direct calls within the discovered services and have `SYSTEM`/`ADMIN` restrictions where noted.

| Service | Method | Direct path | Parameters / body | Restriction |
|---|---|---|---|---|
| users-service | `POST` | `/internal/users/authenticate` | body: `Credentials` | direct internal authentication call used by security-service |
| accounts-service | `GET` | `/api/internal/accounts/interest-due` | query: `asOf*` (date) | `SYSTEM` or `ADMIN` |
| accounts-service | `GET` | `/api/internal/accounts/annual-fees` | none | `SYSTEM` or `ADMIN` |
| accounts-service | `POST` | `/api/internal/accounts/{accountId}/interest-processed` | path: `accountId*`; body: `InterestProcessingRequest` | `SYSTEM` or `ADMIN` |
| transactions-service | `GET` | `/api/internal/fixed-deposits/dependencies` | query: `accountId*` | `SYSTEM` or `ADMIN` |

## Operational endpoints

`scripts/health-check.ps1` checks `GET /actuator/health` directly on the gateway and each backend service, plus the discovery server root. These actuator URLs are direct-service operational APIs; they are not gateway routes. Products explicitly exposes `health` and `info` under management configuration.

## Important implementation notes

- The gateway itself permits only `/auth/**` and `/.well-known/**` without a bearer token; all other gateway paths require a valid JWT.
- The GPT-OSS request fields `customerId`, `transactionId`, `accountId`, and `module` are accepted by `AssistantRequest`, but the controller currently passes only `message` to the configured model runtime.
- Direct security-service configuration currently permits all incoming requests. Use the gateway for normal client traffic; direct service exposure should be restricted at deployment/network level.
- Some API bodies contain server-controlled fields. For example, transactions overwrite `initiatedByUserId` with the current JWT user, and account create/update derive audit user data from the JWT.
