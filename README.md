# Ticketing Management System

Spring Boot 3 / Thymeleaf / Spring Security / JPA / MySQL.

## Run it

1. **Reset the database once** (the table layout changed, so old test data must go). In MySQL:
   `DROP DATABASE ticketing_db;`  The app recreates it and the default accounts on start.
2. Make sure `pom.xml` has these dependencies: `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`,
   `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `mysql-connector-j`. Java 17 or newer.
3. Copy `src/main` over your project's `src/main` and delete the old files listed in CHANGES (below).
4. Run `TicketingManagementSystemApplication`, then open http://localhost:8080

## Default accounts (created automatically)

| Role   | Email               | Password  |
|--------|---------------------|-----------|
| Admin  | admin@system.com    | admin123  |
| Agent  | agent@system.com    | agent123  |
| Agent  | agent2@system.com   | agent123  |
| Client | client@system.com   | client123 |

## Pages

Public: `/` homepage, `/kiosk` walk-up kiosk, `/track` ticket tracking, `/login`, `/register`
Client: `/client/dashboard`, `/tickets/new`, `/tickets`, `/tickets/{id}`, `/profile`
Agent:  `/agent/dashboard`, `/tickets`, `/tickets/{id}`, `/profile`
Admin:  `/admin/dashboard`, `/admin/users`, `/admin/reports`, `/admin/reports/export.csv`

## Delete these old files from your project

- `model/GlobalModelAdvice.java` (duplicate of config/GlobalModelAdvice.java)
- `controller/PageController.java` (replaced by AuthController + PublicController)
- `util/PasswordUtil.java` and `config/AuthInterceptor.java` if they still exist (Spring Security replaces them)
- Template folders `templates/Admindashboard/` and `templates/Client/` (now `templates/admin/` and `templates/client/`)
- `static/css/dashboard.css` (unused), and the `static/images/` folder (logo now lives in `static/img/`)
