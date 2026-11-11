# Implementation Plan: CV Lievera ERP-Lite System

## Overview

An ERP-lite backend covering **95 use cases** across inventory, purchasing, sales, and bookkeeping — built on Spring Boot 3.5.7 / Java 17 / PostgreSQL. The scope expanded from pure stock management to include full bookkeeping (cash/bank ledger, AR/AP, P&L, cash flow, expense tracking) and financial reporting.

**Established conventions (preserve throughout):**
- Domain-grouped packages: `product/`, `supplier/`, `inventory/`, etc. — NOT type-based (`entity/`, `service/`, etc.)
- Lombok `@Data/@Builder/@NoArgsConstructor/@AllArgsConstructor` on entities and DTOs
- Nested `Request`/`Response` static classes inside a single `XxxDTO` class
- `@CreationTimestamp`/`@UpdateTimestamp`, `GenerationType.IDENTITY`
- Service interface + `ServiceImpl`, constructor injection via `@RequiredArgsConstructor`
- Custom exceptions + `@RestControllerAdvice` (`GlobalExceptionHandler`)
- `ddl-auto=validate` — all schema changes via Flyway migrations only

---

## 1. Complete Entity / Table Design

### 1a. Entities Already Implemented

**`Product`** (`product/`) — name, SKU, description, unitPrice, costPrice, quantityInStock, minStockLevel, unit (enum), brand, active, expiryTracked, barcode, category FK, supplier FK

**`Category`** (`product/`) — name, description

**`Unit`** (`product/`) — enum: PCS, KG, GRAM, LITER, ML, BOX, PACK, SACK, DOZEN, PAIR, SET, BOTTLE, BAG, METER, ROLL

**`Supplier`** (`supplier/`) — name, contactPerson, email, phone, address

**`StockTransaction`** (`inventory/`) — product FK, transactionType (STOCK_IN/STOCK_OUT/ADJUSTMENT), quantity, notes, referenceNo, transactionDate, performedBy (User FK), batch FK

**`Batch`** (`inventory/`) — product FK, lotNumber, quantity, expiryDate, receivedDate, costPrice, notes

**`User`** (`user/`) — username, passwordHash, fullName, email, role (ADMIN/STAFF), active, lastLoginAt

**`AuditLog`** (`audit/`) — userId FK, username, action, entityType, entityId, changeSummary, ipAddress, createdAt

---

### 1b. Entities Still to Build

**Phase 3 — Batch completion**
- `StockTransaction` additions: `adjustmentReason` enum `{ DAMAGED, MISCOUNTED, RETURNED, EXPIRED, OTHER }` (required when ADJUSTMENT), `unitCostAtTransaction BigDecimal`

**Phase 4 — Purchasing** (`purchasing/`)
- `PurchaseOrder` — poNumber (unique), supplier FK, status `{ DRAFT, SENT, PARTIALLY_RECEIVED, RECEIVED, CANCELLED, VOIDED }`, orderDate, expectedDate, totalAmount, notes, createdBy FK
- `PurchaseOrderLine` — PO FK, product FK, quantityOrdered, quantityReceived, unitCost, lineTotal
- `GoodsReceipt` — PO FK, receiptNumber, receivedDate, receivedBy FK, notes
- `GoodsReceiptLine` — receipt FK, poLine FK, product FK, quantityReceived, batch FK → creates/updates Batch
- `SupplierInvoice` — PO FK, invoiceNumber, invoiceDate, amount, status `{ UNPAID, PARTIAL, PAID }`
- `DocumentSequence` — docType, prefix, year, lastNumber (pessimistic-locked for reference-number generation)

**Phase 5a — Sales & Customers** (`customer/`)
- `Customer` — name, contactPerson, email, phone, address, paymentTerms, active
- `SalesOrder` — soNumber (unique), customer FK, status `{ DRAFT, CONFIRMED, PARTIALLY_DISPATCHED, DISPATCHED, COMPLETED, CANCELLED, VOIDED }`, orderDate, taxRate (default 0.11 for PPN), totalAmount, taxAmount, grandTotal, notes, createdBy FK
- `SalesOrderLine` — SO FK, product FK, quantityOrdered, quantityDispatched, unitPriceSnapshot (captured at order time — immune to future price changes), lineTotal
- `Dispatch` — SO FK, dispatchNumber, dispatchDate, dispatchedBy FK, notes
- `DispatchLine` — dispatch FK, soLine FK, product FK, batch FK (FEFO-selected), quantity
- `CustomerReturn` — SO FK (nullable), customer FK, returnDate, reason enum `{ DAMAGED, WRONG_ITEM, OVERDELIVERED, OTHER }`, notes, processedBy FK
- `CustomerReturnLine` — return FK, product FK, batch FK (nullable), quantity → restocks

**Phase 5b — Financial Ledger** (`finance/`)
- `CustomerPayment` — customer FK, salesOrder FK (nullable), amount, paymentDate, method `{ CASH, TRANSFER, CREDIT }`, notes
- `SupplierPayment` — supplier FK, purchaseOrder FK (nullable), supplierInvoice FK (nullable), amount, paymentDate, method, notes
- `Expense` — category `{ UTILITIES, TRANSPORT, STAFF, RENT, OTHER }`, amount, expenseDate, description, referenceNo

**Phase 6 — Stock Take** (`inventory/`)
- `StockTake` — reference, categoryFilter FK (nullable = full count), status `{ IN_PROGRESS, COMPLETED, CANCELLED }`, startedAt, completedAt, conductedBy FK
- `StockTakeLine` — stockTake FK, product FK, batch FK (nullable), systemQuantity, countedQuantity, discrepancy (computed), resolved

**Phase 8 — Settings & Notifications** (`settings/`, `notification/`)
- `SystemSetting` — single-row: companyName, companyAddress, logoPath, defaultCurrency (IDR), timezone, paperWidthMm, paperHeightMm, expiryAlertDays, ppnRate (0.11)
- `Currency` — code PK, name, symbol, exchangeRateToBase, active
- `Notification` — user FK (nullable = broadcast), type `{ LOW_STOCK, EXPIRY, OVERDUE_PAYMENT, PENDING_ORDER }`, message, relatedEntityType, relatedEntityId, readAt, createdAt

---

### 1c. Relationship Map

```
Category 1──* Product *──1 Supplier
Product 1──* Batch
Product 1──* StockTransaction *──1 Batch (nullable)
User 1──* StockTransaction (performedBy)
Supplier 1──* PurchaseOrder 1──* PurchaseOrderLine *──1 Product
PurchaseOrder 1──* GoodsReceipt 1──* GoodsReceiptLine ──> creates/updates Batch
PurchaseOrder 1──* SupplierInvoice
Supplier 1──* SupplierPayment *──1 PurchaseOrder (nullable)
Customer 1──* SalesOrder 1──* SalesOrderLine *──1 Product
SalesOrder 1──* Dispatch 1──* DispatchLine *──1 Batch (FEFO)
SalesOrder 1──* CustomerReturn 1──* CustomerReturnLine
Customer 1──* CustomerPayment *──1 SalesOrder (nullable)
StockTake 1──* StockTakeLine *──1 Product
```

---

## 2. Package Structure

```
com.cvlievera.stockmanagement
├── auth/           # JWT auth — login, refresh, logout
├── user/           # User entity, CRUD, roles
├── audit/          # AuditLog, @Auditable AOP
├── security/       # JWT filter, SecurityConfig, UserPrincipal
├── product/        # Category, Product, Unit ← DONE
├── supplier/       # Supplier ← DONE
├── inventory/      # StockTransaction, Batch, FEFO service, StockTake
├── purchasing/     # PurchaseOrder, GoodsReceipt, SupplierInvoice
├── customer/       # Customer, SalesOrder, Dispatch, CustomerReturn
├── finance/        # CustomerPayment, SupplierPayment, Expense
├── report/         # Report query services, exporters
├── document/       # PDF generation (Delivery Order, Invoice, PO)
├── dashboard/      # Dashboard aggregate queries
├── notification/   # Notification entity, scheduled alert jobs
├── settings/       # SystemSetting, Currency, DocumentSequence
├── common/dto/     # ApiResponse<T>, PageResponse<T>
├── exception/      # GlobalExceptionHandler, domain exceptions
└── config/         # DataInitializer
```

---

## 3. Phased Build Order

| Phase | Modules | UCs |
|-------|---------|-----|
| **0 — Foundation** | Flyway, validation, API envelope, pagination | — |
| **1 — Auth & Audit** | JWT, User CRUD, RBAC, audit log | UC-01 to UC-05 |
| **2 — Products** | Enriched Product, Category, Unit, low-stock query | UC-06 to UC-12 |
| **3 — Inventory** | Batch, FEFO, adjustment reason, stock take | UC-13 to UC-22 |
| **4 — Purchasing** | PO lifecycle, goods receipt, supplier invoices | UC-23 to UC-31 |
| **5a — Sales** | Customer, SalesOrder (price snapshot, PPN), dispatch, returns | UC-32 to UC-43 |
| **5b — Financial Bookkeeping** | CustomerPayment, SupplierPayment, Expense, AR/AP | UC-44 to UC-51 |
| **6 — Stock Take** | StockTake, StockTakeLine, reconciliation | UC-22 |
| **7 — Reports & Dashboard** | All report services, dashboard aggregates, Excel/PDF export | UC-52 to UC-67, UC-85 to UC-88 |
| **8 — Documents** | PDF Delivery Order, Invoice, PO — custom paper size | UC-68 to UC-71 |
| **9 — Notifications, Settings, Data** | Alerts, SystemSetting, Currency, import/export, backup | UC-72 to UC-84, UC-89 to UC-95 |
| **10 — QA** | Integration tests (Testcontainers), OpenAPI docs, 80%+ coverage | — |

> Phases 5a and 5b can begin simultaneously after Phase 4. Phase 6 (Stock Take) can slot into Phase 3 as T-27. Phases 7 and 8 can overlap once Phase 5b is done.

---

## 4. Per-Module API Contract Summary

All list endpoints return `PageResponse<T>` with `?page&size&sort`. All responses wrapped in `ApiResponse<T>`.

### Auth & Users (Phase 1) — DONE
- `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`
- `GET/POST/PUT/DELETE /api/users` (ADMIN)
- `GET /api/audit-logs?userId&entityType&from&to` (ADMIN)

### Products (Phase 2) — DONE
- Full CRUD + `GET /api/products/low-stock` + `GET /api/products/{id}/stock`
- `?search&categoryId&brand&active` filters + `PUT /api/products/{id}/deactivate|activate`

### Inventory / Batch (Phase 3)
- `GET/POST/PUT /api/batches`, `GET /api/products/{id}/batches`
- `GET /api/batches/fefo?productId&quantity` — FEFO allocation recommendation
- `GET /api/products/{id}/movements` — full movement history
- `GET /api/alerts/low-stock`, `GET /api/alerts/expiring?days=X`
- `POST /api/stock-takes`, `POST /api/stock-takes/{id}/lines`, `POST /api/stock-takes/{id}/complete`

### Purchasing (Phase 4)
- `GET/POST/PUT /api/purchase-orders`, `POST /api/purchase-orders/{id}/send`
- `POST /api/purchase-orders/{id}/receipts` (goods receipt → batches, partial supported)
- `POST /api/purchase-orders/{id}/void`
- `GET /api/purchase-orders/{id}/invoices`, `POST /api/purchase-orders/{id}/invoices`

### Sales & Customers (Phase 5a)
- `GET/POST/PUT/DELETE /api/customers`
- `GET/POST /api/sales-orders`, `POST /api/sales-orders/{id}/confirm`
- `POST /api/sales-orders/{id}/dispatches` (FEFO deduction, partial delivery)
- `POST /api/sales-orders/{id}/returns`, `POST /api/sales-orders/{id}/void`
- Response includes `taxRate`, `taxAmount`, `grandTotal` fields

### Financial Bookkeeping (Phase 5b)
- `GET/POST /api/payments/customer`, `GET/POST /api/payments/supplier`
- `GET/POST /api/expenses`
- `GET /api/finance/ar` — Accounts Receivable summary
- `GET /api/finance/ap` — Accounts Payable summary
- `GET /api/finance/pl?from&to` — P&L summary
- `GET /api/finance/cashflow?from&to` — cash flow summary

### Reports & Dashboard (Phase 7)
- `GET /api/reports/stock-levels`, `/stock-movements?from&to`, `/expiring?days`, `/low-stock`
- `GET /api/reports/sales?from&to`, `/purchases?from&to`
- `GET /api/reports/customer-balances`, `/supplier-balances`
- `GET /api/reports/pl?from&to`, `/expenses?from&to`, `/cashflow?from&to`
- `GET /api/reports/top-products?from&to`, `/top-customers?from&to`, `/slow-moving?days`
- `?format=pdf|excel` on all report endpoints
- `GET /api/dashboard/summary`

### Documents (Phase 8)
- `GET /api/sales-orders/{id}/delivery-order.pdf`
- `GET /api/sales-orders/{id}/invoice.pdf`
- `GET /api/purchase-orders/{id}/print.pdf`
- Paper size from `SystemSetting.paperWidthMm` × `paperHeightMm`

### Notifications & Settings (Phase 9)
- `GET /api/notifications`, `PUT /api/notifications/{id}/read`
- `GET/PUT /api/settings`, `GET/PUT /api/currencies`
- `POST /api/import/products` (CSV/Excel), `GET /api/export/backup`
- `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`

---

## 5. Cross-Cutting Concerns

### Indonesian PPN (11%)
- `SalesOrder` carries `taxRate` (default 0.11 from `SystemSetting.ppnRate`)
- `taxAmount = SUM(lineTotal) * taxRate`, `grandTotal = SUM(lineTotal) + taxAmount`
- Admin can override rate per order; rate change does not affect historical orders

### Price Snapshot on Sales Orders
- `SalesOrderLine.unitPriceSnapshot` is set from `Product.unitPrice` at time of order creation
- Subsequent `Product` price edits have no effect on existing SOs
- Historical P&L uses `unitPriceSnapshot`, not current product price

### FEFO Logic
- `FefoAllocationService.allocate(productId, quantity)` — returns ordered list of batch slices sorted by `expiryDate ASC`, skipping depleted/expired batches
- Raises `InsufficientStockException` if total available < requested
- Used by both manual dispatch and stock-out transactions

### Reference Number Generation (UC-71)
- `DocumentSequenceService.next(docType)` — pessimistic lock on `document_sequences` row
- Format: `PREFIX-YYYY-NNNNN` (e.g. `SO-2026-00042`, `PO-2026-00011`)

### Currency / IDR
- Default currency IDR set in `SystemSetting`; every monetary entity carries `currencyCode` defaulting to IDR
- Multi-currency activatable later without schema change — `Currency` table exists from Phase 9

### Role-Based Access
- `ADMIN`: full access including financial reports, ledger, user management, system settings
- `STAFF`: inventory operations, SO/PO creation, customer/supplier management; no access to financial reports or ledger

---

## 6. Risk Register

| Risk | Severity | Mitigation |
|------|----------|------------|
| `quantityInStock` drift vs `SUM(batch.quantity)` | HIGH | Keep them synced via `StockTransactionServiceImpl`; reconciliation query in stock-take |
| Concurrent dispatch overselling | HIGH | Pessimistic lock on batch rows during FEFO allocation; `@Version` on SalesOrderLine |
| FEFO correctness (multi-batch split, partial, expired) | HIGH | Dedicated `FefoAllocationService` with unit tests covering all edge cases |
| PPN rate change affecting historical SOs | HIGH | Snapshot `taxRate` on SalesOrder at creation; rate change only affects new orders |
| Price change affecting historical SOs | HIGH | `unitPriceSnapshot` on `SalesOrderLine` — set at creation, never updated |
| Partial receipt / partial delivery state machine | HIGH | Recompute order status from line quantities after each event; never set status directly |
| Reference-number race conditions | MEDIUM | Pessimistic lock in `DocumentSequenceService`; unique DB constraint as backstop |
| Void/cancel stock reversal errors | MEDIUM | Void only if status permits; reverse stock atomically in same transaction as status change |
| AR/AP calculations spanning large date ranges | MEDIUM | Indexed queries on payment dates + order dates; consider materialized views if slow |
| PDF custom paper size rendering | MEDIUM | iText `Rectangle` from mm→points conversion (`mm * 2.8346`); test on physical printer |
| Flyway migration ordering as migrations grow | LOW | Strict sequential versioning; never re-use a version number |
| `ADJUSTMENT` signed-delta migration from old absolute-set behaviour | LOW | Pre-production; document the breaking change, add mandatory-reason validation |

---

## 7. Task List

### Phase 0 — Foundation
- [x] T-01: Flyway baseline migration V1; set `ddl-auto=validate`
- [x] T-02: Externalize DB + JWT credentials to env vars
- [x] T-03: Bean Validation on all Request DTOs
- [x] T-04: `GlobalExceptionHandler` — validation + constraint violation handlers
- [x] T-05: `ApiResponse<T>` + `PageResponse<T>`; retrofit all controllers
- [x] T-06: Pageable on all list endpoints

### Phase 1 — Auth & Audit (UC-01 to UC-05)
- [x] T-07: Spring Security + JWT — stateless, BCrypt, `SecurityConfig`
- [x] T-08: `User` entity + `V2__users.sql`
- [x] T-09: `UserDetailsServiceImpl`, `JwtTokenProvider`, `JwtAuthFilter`
- [x] T-10: `/api/auth` — login / refresh / logout
- [x] T-11: User CRUD (`/api/users`, ADMIN)
- [x] T-12: RBAC — `@PreAuthorize` on existing endpoints
- [x] T-13: `AuditLog` entity + `V3__audit_log.sql`
- [x] T-14: `@Auditable` AOP aspect + `/api/audit-logs` (ADMIN)

### Phase 2 — Products (UC-06 to UC-12)
- [x] T-15: `V4__product_enrichment.sql` — unit, brand, costPrice, isActive, expiryTracked, barcode
- [x] T-16: `Product` entity + `Unit` enum; `ProductDTO` update
- [x] T-17: `ProductServiceImpl` — active filter, brand filter, deactivate
- [x] T-18: `PUT /api/products/{id}/deactivate|activate` + query params
- [x] T-19: `GET /api/products/{id}/stock`
- [ ] T-20b: Add unit conversion fields to `Product` — `sellingUnit`, `conversionFactor` (UC-12); migration V6a

### Phase 3 — Inventory: Batch, Expiry, FEFO (UC-13 to UC-22)
- [~] T-20: `Batch` entity + `V5__batch_and_transaction_performer.sql` — batch table + performed_by on transactions *(entity + migration done; seeding existing products pending)*
- [~] T-21: `StockTransaction` additions — `adjustmentReason` enum + `unitCostAtTransaction`; migration V6 *(performedBy + batch FK done; adjustmentReason pending)*
- [ ] T-22: `StockTransactionServiceImpl` — batch-aware stock-in/out; mandatory reason on ADJUSTMENT; seed existing products into one batch each
- [ ] T-23: `FefoAllocationService` with unit tests (multi-batch, expired exclusion, insufficient stock)
- [ ] T-24: `/api/batches` CRUD + `GET /api/batches/fefo`
- [ ] T-25: `GET /api/products/{id}/movements` — full history with performedBy
- [ ] T-26: `GET /api/alerts/low-stock`, `GET /api/alerts/expiring?days=X`
- [ ] T-27: `StockTake` / `StockTakeLine` entities + migration V7; start/count/complete endpoints

### Phase 4 — Purchasing (UC-23 to UC-31)
- [ ] T-28: Add `paymentTerms` to `Supplier` (migration V8); update DTO
- [ ] T-29: `DocumentSequence` table + `DocumentSequenceService` (migration V9)
- [ ] T-30: `PurchaseOrder` / `PurchaseOrderLine` entities + migration V10; PO CRUD + send + void (UC-31)
- [ ] T-31: `GoodsReceipt` / `GoodsReceiptLine` + migration V11; receive endpoint (creates batches, partial receipt, updates PO status)
- [ ] T-32: Outstanding/partial PO query endpoint (UC-26)
- [ ] T-33: `SupplierInvoice` + migration V12; invoice endpoints linked to PO (UC-27)

### Phase 5a — Sales & Customers (UC-32 to UC-43)
- [ ] T-34: `Customer` entity + migration V13; Customer CRUD (UC-32)
- [ ] T-35: `SalesOrder` / `SalesOrderLine` + migration V14; SO CRUD with `unitPriceSnapshot` (UC-34) + PPN `taxRate/taxAmount/grandTotal` (UC-35)
- [ ] T-36: SO confirm + void endpoints; status state machine (UC-43)
- [ ] T-37: `Dispatch` / `DispatchLine` + migration V15; dispatch endpoint using FEFO + partial delivery tracking (UC-37, UC-38)
- [ ] T-38: `CustomerReturn` / `CustomerReturnLine` + migration V16; return endpoint (restock with reason) (UC-39)
- [ ] T-39: Customer payment status tracking on SO (UC-40); `GET /api/customers/{id}/outstanding` (UC-42)

### Phase 5b — Financial Bookkeeping (UC-44 to UC-51)
- [ ] T-40: `CustomerPayment` + `SupplierPayment` + `Expense` entities + migration V17
- [ ] T-41: `POST /api/payments/customer`, `POST /api/payments/supplier` — record payments linked to SO/PO
- [ ] T-42: `POST /api/expenses` — categorised non-inventory expenses
- [ ] T-43: AR endpoint `GET /api/finance/ar` — outstanding per customer
- [ ] T-44: AP endpoint `GET /api/finance/ap` — outstanding per supplier
- [ ] T-45: P&L summary `GET /api/finance/pl?from&to` — revenue vs expenses
- [ ] T-46: Cash flow summary `GET /api/finance/cashflow?from&to`

### Phase 7 — Reports & Dashboard (UC-52 to UC-67, UC-85 to UC-88)
- [ ] T-47: Stock report services — levels, movements, expiring, low-stock (UC-52 to UC-55)
- [ ] T-48: Sales/purchase fulfilment report services (UC-56, UC-57)
- [ ] T-49: Customer/supplier balance report services (UC-58, UC-59)
- [ ] T-50: Financial report services — P&L, expense breakdown, cash flow (UC-60 to UC-62)
- [ ] T-51: Analytics — top products, top customers, slow-moving stock (UC-63 to UC-66)
- [ ] T-52: `?format=pdf|excel` export on all reports; add Apache POI dep (UC-67)
- [ ] T-53: Dashboard aggregate endpoint `GET /api/dashboard/summary` (UC-85 to UC-88)

### Phase 8 — Documents & Printing (UC-68 to UC-71)
- [ ] T-54: Add iText/OpenPDF dep; `PdfDocumentService` with custom paper size (mm → points)
- [ ] T-55: Delivery Order / Sales Invoice PDF (UC-68)
- [ ] T-56: Purchase Order PDF (UC-69)
- [ ] T-57: Configurable document header from `SystemSetting` — company name, logo, address (UC-70)

### Phase 9 — Notifications, Settings, Data Management (UC-72 to UC-84, UC-89 to UC-95)
- [ ] T-58: `SystemSetting` + `Currency` + `DocumentSequence` entities + migration V18; seed IDR + default settings (UC-78 to UC-84, UC-89)
- [ ] T-59: `Notification` entity + migration V19; `GET /api/notifications`, mark-read (UC-91)
- [ ] T-60: `@Scheduled` jobs — low-stock alert, expiry warning, overdue payment alerts, pending order alerts (UC-72 to UC-76)
- [ ] T-61: Notification preferences admin toggle (UC-91)
- [ ] T-62: Soft delete / archive on customers, suppliers, orders (UC-92)
- [ ] T-63: Global search endpoint `GET /api/search?q=` (UC-93)
- [ ] T-64: Password reset via email — forgot-password + reset endpoints (UC-90)
- [ ] T-65: CSV/Excel product + stock import with validation error report (UC-81)
- [ ] T-66: Full data export + `@Scheduled` pg_dump backup (UC-82, UC-84)

### Phase 10 — QA & Polish
- [ ] T-67: Add `springdoc-openapi`; document all endpoints
- [ ] T-68: Integration tests (Testcontainers PostgreSQL) — FEFO, partial-delivery, partial-receipt, void/cancel, stock-take reconciliation
- [ ] T-69: JaCoCo coverage gate at 80%+
- [ ] T-70: Admin activity feed — `/api/audit-logs` filtered view (UC-95)
