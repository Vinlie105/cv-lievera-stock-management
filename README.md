# CV Lievera Stock Management System

ERP-lite system for CV Lievera — inventory, purchasing, sales, and bookkeeping in one backend.
Java 17 · Spring Boot 3.5.7 · PostgreSQL · Flyway.

## Documentation

- [`docs/TODO.md`](docs/TODO.md) — task tracker (current progress across all phases)
- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — full implementation plan (entity design, phased build order, API contracts, risk register)
- [`Use cases.txt`](Use%20cases.txt) — 95 use cases across all modules

## Domain Package Structure

```
com.cvlievera.stockmanagement
├── auth/          # JWT login/refresh/logout
├── user/          # User entity, CRUD, roles (ADMIN/STAFF)
├── audit/         # AuditLog, @Auditable AOP aspect
├── security/      # JwtTokenProvider, JwtAuthFilter, SecurityConfig
├── product/       # Category, Product, Unit enum — entity/dto/repo/service/controller
├── supplier/      # Supplier — entity/dto/repo/service/controller
├── inventory/     # StockTransaction, Batch — entity/dto/repo/service/controller
├── common/dto/    # ApiResponse<T>, PageResponse<T>
├── exception/     # GlobalExceptionHandler, ResourceNotFoundException, BadRequestException
└── config/        # DataInitializer
```

Upcoming modules (planned):
`purchasing/` · `customer/` · `finance/` · `report/` · `document/` · `notification/` · `settings/` · `dashboard/`

## Local Development Setup

1. Create `src/main/resources/application-local.properties` (git-ignored):
   ```properties
   DB_USERNAME=cv_lievera_user
   DB_PASSWORD=your_password
   JWT_SECRET=your_32_char_minimum_secret_here
   ```
2. Activate the `local` profile in your IDE run config: `SPRING_PROFILES_ACTIVE=local`
3. Database: `cv_lievera_stock` on PostgreSQL localhost:5432

## Tech Stack

| Concern | Library |
|---------|---------|
| Runtime | Java 17 / Spring Boot 3.5.7 |
| Database | PostgreSQL + Spring Data JPA |
| Schema migrations | Flyway |
| Security | Spring Security + JJWT 0.12.3 |
| Validation | Spring Boot Validation (Bean Validation) |
| Audit | Spring AOP (`@Auditable` aspect) |
| Boilerplate | Lombok |
| Build | Maven |

Planned additions: iText/OpenPDF (PDF printing) · Apache POI (Excel export) · OpenCSV (CSV import) · Spring Mail (email alerts) · Quartz (scheduled jobs)

## Flyway Migrations

| Version | Description |
|---------|-------------|
| V1 | Baseline schema — categories, suppliers, products, stock_transactions |
| V2 | Users table |
| V3 | Audit log table + indexes |
| V4 | Product enrichment — unit, brand, cost_price, is_active, expiry_tracked, barcode |
| V5 | Batches table + performed_by on stock_transactions |

## Security model & web app

The PWA is served by Spring Boot itself from `src/main/resources/static` (same origin, no CORS, no CDN; CSP is `'self'` only). Open http://localhost:8080/.

- **Sessions:** httpOnly + SameSite=Strict cookies (`cvl_at` 15 min JWT, `cvl_rt` opaque rotating refresh token, hashed in DB, reuse detection, 30 min idle / 12 h absolute). CSRF double-submit cookie.
- **Login:** 5 failures lock the account 15 min; 10 req/min/IP on credential endpoints; generic error messages.
- **2FA:** TOTP (RFC 6238) mandatory for ADMIN, optional for STAFF; 10 one-time recovery codes; seeds AES-256-GCM encrypted (`APP_ENCRYPTION_KEY`).
- **Passwords:** 12+ chars, 3 of 4 classes; temp passwords force a change at first login. First admin comes from `BOOTSTRAP_ADMIN_PASSWORD` or a random password logged once.
- **Roles:** STAFF = read + record stock movements; everything else ADMIN.
- **Audit:** SHA-256 hash chain + DB trigger blocks UPDATE/DELETE; `GET /api/audit-logs/verify`.

Required env vars: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (32+ chars), `APP_ENCRYPTION_KEY` (`openssl rand -base64 32`). Production: run behind HTTPS, keep `app.security.cookie-secure=true`, set `SERVER_FORWARD_HEADERS=native` behind a proxy.
