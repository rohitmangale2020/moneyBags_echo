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

Set MySQL connection values if needed (defaults point to local MySQL), then build:

```powershell
mvn clean verify
```

Start a service, for example:

```powershell
mvn -pl users spring-boot:run
```

Start the services in this order: `users`, `security-service`, then
`api-gateway-service`. For example, run `mvn -pl security-service
spring-boot:run` after the users service is available.
