# CV Lievera ERP-Lite — Task Tracker

Status legend: `[ ]` todo · `[x]` done · `[~]` in progress · `[-]` skipped/deferred

---

## Phase 0 — Foundation
- [x] T-01: Flyway baseline migration V1; set `ddl-auto=validate`
- [x] T-02: Externalize DB + JWT credentials to env vars
- [x] T-03: Bean Validation on all Request DTOs
- [x] T-04: `GlobalExceptionHandler` — validation + constraint violation handlers
- [x] T-05: `ApiResponse<T>` + `PageResponse<T>`; retrofit all controllers
- [x] T-06: Pageable on all list endpoints

## Phase 1 — Auth & Audit (UC-01 to UC-05)
- [x] T-07: Spring Security + JWT — stateless, BCrypt, `SecurityConfig`
- [x] T-08: `User` entity + `V2__users.sql`
- [x] T-09: `UserDetailsServiceImpl`, `JwtTokenProvider`, `JwtAuthFilter`
- [x] T-10: `/api/auth` — login / refresh / logout
- [x] T-11: User CRUD (`/api/users`, ADMIN)
- [x] T-12: RBAC — `@PreAuthorize` on existing endpoints
- [x] T-13: `AuditLog` entity + `V3__audit_log.sql`
- [x] T-14: `@Auditable` AOP aspect + `/api/audit-logs` (ADMIN)

## Phase 2 — Products (UC-06 to UC-12)
- [x] T-15: `V4__product_enrichment.sql` — unit, brand, costPrice, isActive, expiryTracked, barcode
- [x] T-16: `Product` entity + `Unit` enum; `ProductDTO` update
- [x] T-17: `ProductServiceImpl` — active filter, brand filter, deactivate
- [x] T-18: `PUT /api/products/{id}/deactivate|activate` + `?brand&active` query params
- [x] T-19: `GET /api/products/{id}/stock`
- [x] T-49: Package refactor — type-based → domain-grouped (`product/`, `supplier/`, `inventory/`)
- [ ] T-20b: Add `sellingUnit` + `conversionFactor` to `Product`; migration V6a (UC-12)

## Phase 3 — Inventory: Batch, Expiry, FEFO (UC-13 to UC-22)
- [~] T-20: `Batch` entity + `V5__batch_and_transaction_performer.sql` *(entity + migration done — seeding existing products into one batch each pending)*
- [~] T-21: `StockTransaction` additions — `adjustmentReason` enum + `unitCostAtTransaction`; migration V6 *(performedBy + batch FK done — adjustmentReason + unitCost pending)*
- [ ] T-22: `StockTransactionServiceImpl` — batch-aware stock-in/out; mandatory reason on ADJUSTMENT; seed existing products into initial batches
- [ ] T-23: `FefoAllocationService` with unit tests (multi-batch split, expired exclusion, insufficient stock)
- [ ] T-24: `/api/batches` CRUD + `GET /api/batches/fefo?productId&quantity`
- [ ] T-25: `GET /api/products/{id}/movements` — full history with `performedByUsername`
- [ ] T-26: `GET /api/alerts/low-stock`, `GET /api/alerts/expiring?days=X`
- [ ] T-27: `StockTake` / `StockTakeLine` entities + migration V7; start/count/complete endpoints

## Phase 4 — Purchasing (UC-23 to UC-31)
- [ ] T-28: Add `paymentTerms` to `Supplier`; migration V8; update DTO
- [ ] T-29: `DocumentSequence` table + `DocumentSequenceService`; migration V9
- [ ] T-30: `PurchaseOrder` / `PurchaseOrderLine` entities + migration V10; PO CRUD + send + void (UC-31)
- [ ] T-31: `GoodsReceipt` / `GoodsReceiptLine` + migration V11; receive endpoint — creates batches, partial receipt, updates PO status (UC-25, UC-26)
- [ ] T-32: Outstanding / partial PO query endpoint (UC-26)
- [ ] T-33: `SupplierInvoice` + migration V12; invoice endpoints linked to PO (UC-27)

## Phase 5a — Sales & Customers (UC-32 to UC-43)
- [ ] T-34: `Customer` entity + migration V13; Customer CRUD (UC-32)
- [ ] T-35: `SalesOrder` / `SalesOrderLine` + migration V14; SO CRUD with `unitPriceSnapshot` (UC-34) + PPN `taxRate/taxAmount/grandTotal` (UC-35)
- [ ] T-36: SO confirm + void endpoints; status state machine (UC-37, UC-43)
- [ ] T-37: `Dispatch` / `DispatchLine` + migration V15; dispatch endpoint using FEFO + partial delivery tracking (UC-37, UC-38)
- [ ] T-38: `CustomerReturn` / `CustomerReturnLine` + migration V16; return endpoint — restock with reason (UC-39)
- [ ] T-39: Customer payment status tracking on SO (UC-40); `GET /api/customers/{id}/outstanding` (UC-42)

## Phase 5b — Financial Bookkeeping (UC-44 to UC-51)
- [ ] T-40: `CustomerPayment` + `SupplierPayment` + `Expense` entities + migration V17
- [ ] T-41: `POST /api/payments/customer`, `POST /api/payments/supplier` — record payments linked to SO / PO (UC-45, UC-46)
- [ ] T-42: `POST /api/expenses` — categorised non-inventory expenses (UC-47)
- [ ] T-43: AR endpoint `GET /api/finance/ar` — outstanding per customer (UC-48)
- [ ] T-44: AP endpoint `GET /api/finance/ap` — outstanding per supplier (UC-49)
- [ ] T-45: P&L summary `GET /api/finance/pl?from&to` (UC-50)
- [ ] T-46: Cash flow summary `GET /api/finance/cashflow?from&to` (UC-51)

## Phase 7 — Reports & Dashboard (UC-52 to UC-67, UC-85 to UC-88)
- [ ] T-47: Stock reports — levels, movements, expiring, low-stock (UC-52 to UC-55)
- [ ] T-48: Sales / purchase fulfilment reports (UC-56, UC-57)
- [ ] T-49r: Customer / supplier balance reports (UC-58, UC-59)
- [ ] T-50: Financial reports — P&L, expense breakdown, cash flow (UC-60 to UC-62)
- [ ] T-51: Analytics — top products, top customers, slow-moving (UC-63 to UC-66)
- [ ] T-52: `?format=pdf|excel` export; add Apache POI dep (UC-67)
- [ ] T-53: Dashboard aggregate `GET /api/dashboard/summary` (UC-85 to UC-88)

## Phase 8 — Documents & Printing (UC-68 to UC-71)
- [ ] T-54: Add iText/OpenPDF; `PdfDocumentService` with custom paper size (mm → points)
- [ ] T-55: Delivery Order / Sales Invoice PDF (UC-68)
- [ ] T-56: Purchase Order PDF (UC-69)
- [ ] T-57: Configurable document header from `SystemSetting` (UC-70)

## Phase 9 — Notifications, Settings, Data Management (UC-72 to UC-84, UC-89 to UC-95)
- [ ] T-58: `SystemSetting` + `Currency` entities + migration V18; seed IDR default (UC-78 to UC-84, UC-89)
- [ ] T-59: `Notification` entity + migration V19; `GET /api/notifications`, mark-read (UC-91)
- [ ] T-60: `@Scheduled` alert jobs — low-stock, expiry, overdue payments, pending orders (UC-72 to UC-76)
- [ ] T-61: Notification preferences admin toggle (UC-91)
- [ ] T-62: Soft delete / archive — customers, suppliers, orders (UC-92)
- [ ] T-63: Global search `GET /api/search?q=` across products/customers/suppliers/orders (UC-93)
- [ ] T-64: Password reset via email — forgot-password + reset endpoints (UC-90)
- [ ] T-65: CSV / Excel product + stock import with validation error report (UC-81)
- [ ] T-66: Full data export + `@Scheduled` pg_dump backup (UC-82, UC-84)

## Phase 10 — QA & Polish
- [ ] T-67: `springdoc-openapi` — document all endpoints
- [ ] T-68: Integration tests (Testcontainers) — FEFO, partial delivery, partial receipt, void/cancel, stock-take reconciliation
- [ ] T-69: JaCoCo coverage gate at 80%+
- [ ] T-70: Admin activity feed — `/api/audit-logs` filtered view (UC-95)
