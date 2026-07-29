# Banking Platform

A Maven multi-module Spring Boot starter containing six independent services:
`users`, `customers`, `products`, `accounts`, `transactions`, and `audit`.

Every service includes Spring Web, Spring Web Services, Spring Security, MySQL
Driver, Spring Cloud Gateway, and Resilience4j. Eureka is intentionally not
configured yet; it can be added later as a separate infrastructure service.

## Run

Set MySQL connection values if needed (defaults point to local MySQL), then build:

```powershell
mvn clean verify
```

Start a service, for example:

```powershell
mvn -pl users spring-boot:run
```
