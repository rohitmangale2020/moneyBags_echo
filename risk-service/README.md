# Risk Service

Risk Service is the Spring Boot boundary between the transaction workflow and the Python model scoring API.

## Standalone Maven build

Build or run this service from its own module directory:

```powershell
cd C:\Project\codes\moneyBags_echo\risk-service
mvn clean test
mvn spring-boot:run
```

In an IDE, import `risk-service/pom.xml` as a Maven project when working on this
service directly.

## Responsibilities

- `POST /api/risk/assessments`: creates an immutable risk assessment before money is posted.
- Calls the Python `POST /score` API with only pre-transaction fields and recipient history.
- Stores model scores, risk level, reasons, and model versions.
- `POST /api/risk/profiles/approved-transactions`: updates user and recipient profiles only after a normal approved completion.

It does not post funds. Transaction Service decides whether to continue, verify, or hold the transaction.

## Configuration

```text
MODEL_SCORING_SERVICE_URL=http://localhost:8001
DBURL=...
DBUSER=...
DBPASSWORD=...
JWT_ISSUER=http://localhost:8087
EUREKA_SERVER_URL=http://localhost:8761/eureka
```

## Example assessment request

```json
{
  "transactionRef": "TRX-1001",
  "transactionType": "TRANSFER",
  "amount": 60000,
  "currencyCode": "INR",
  "debitAccountId": "account-a",
  "creditAccountId": "account-b",
  "initiatedByCustomerId": "customer-a",
  "oldBalanceOrg": 90000,
  "oldBalanceDest": 12000,
  "occurredAt": "2026-08-16T03:30:00"
}
```

`TRANSFER` and `WITHDRAWAL` are currently in the PaySim-trained model scope. A `DEPOSIT` is recorded as `NOT_SCORED` by the Python scorer.
