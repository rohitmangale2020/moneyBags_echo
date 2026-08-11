# Repository Guidelines

## Project Structure & Module Organization

This is a Maven multi-module Spring Boot banking platform. The root `pom.xml`
aggregates six independent services: `users`, `customers`, `products`,
`accounts`, `transactions`, and `audit`. Each service keeps production code in
`<module>/src/main/java/com/training/platform/<module>/` and configuration in
`<module>/src/main/resources/application.yml`. Keep service-specific classes
inside that service's package; do not introduce cross-module source dependencies
without updating the Maven module design.

## Build, Test, and Development Commands

- `mvn clean verify` - compiles every module and runs the full Maven lifecycle.
- `mvn test` - runs the repository's unit tests when test sources are present.
- `mvn -pl users spring-boot:run` - starts one service; replace `users` with the
  target module, such as `accounts`.
- `mvn -pl users -am package` - packages one service and any required Maven
  reactor modules.

Use JDK 17. Configure local database access through `DB_URL`, `DB_USERNAME`,
and `DB_PASSWORD`; never commit real credentials or local overrides.

## Coding Style & Naming Conventions

Use four spaces for Java indentation and follow the existing Spring Boot
layout. Packages are lowercase and service-scoped (for example,
`com.training.platform.users.model`). Classes use PascalCase; methods and
fields use camelCase. Name Spring entry points `<Service>Application`, REST
controllers `*Controller`, services `*Service`, and DTOs by their role. Keep
`application.yml` keys hierarchical and environment values parameterized with
`${VARIABLE:default}`. No formatter or linter is currently configured; format
changed Java files consistently before committing.

## Testing Guidelines

There are currently no committed test sources or coverage threshold. Add tests
under `<module>/src/test/java` mirroring production packages, with names ending
in `Test` (for example, `UsersServiceTest`). Add the necessary Spring Boot test
dependency when introducing tests, keep tests isolated from shared databases,
and run `mvn test` before opening a pull request.

## Commit & Pull Request Guidelines

History uses short, imperative-style summaries (for example, `Files restructured
according to modules`). Write focused commits in that style, covering one
logical change. Pull requests should explain the affected service(s), behavior
and configuration changes, validation performed, and linked issue when
available. Include request/response examples or screenshots for user-visible
API or UI changes.
