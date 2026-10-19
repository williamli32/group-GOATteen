# Sprint 6

## Sprint Goal

Implement internal reporting and business insights for the LEAP trading platform, provide analyst and operations interfaces, and verify that reporting figures reconcile with persisted trading records without moving analytical queries onto the live execution path.

**Sprint Goal Status: COMPLETE**

Sprint 6 primarily covers **BR-16 — reporting and analysis** and **BR-17 — business insights**. The operations interface also makes Sprint 5's BR-14/BR-15 audit capabilities available to David, the head of trading operations. Priya, the commercial analyst, receives historical reporting, instrument activity, client activity and period comparisons.

This document records the Sprint 6 work reviewed on `feature/sprint7/goal1` at commit `28e0723`. Watchlists, live daily price-change indicators and the tabbed customer dashboard belong to Sprint 7 and are excluded from the Sprint 6 deliverables below.

---

## User Stories

### 1. Reporting Data Foundation and Synchronization

As an internal stakeholder, I want a historical reporting dataset derived from committed trading records so that I can analyse activity without repeatedly aggregating the operational order and settlement tables.

#### Acceptance Criteria

- [x] A dedicated `reporting` schema stores analytical data
- [x] `reporting.trade_facts` stores one denormalized execution fact per source Fill
- [x] Trade facts retain source order/Fill identifiers, account/client dimensions, instrument/currency dimensions, execution values, timestamps and quote context
- [x] `reporting.order_facts` stores order outcomes, including rejected orders without Fills
- [x] Order facts preserve submission time, completion time, current status and rejection reason
- [x] Reporting synchronization is triggered asynchronously after the originating transaction commits
- [x] Reporting synchronization uses a separate transaction from trade settlement
- [x] Fill uniqueness and order identity prevent replay from creating duplicate facts
- [x] Order synchronization updates the existing fact when an order's status changes
- [x] Trade synchronization records attempt status in `reporting.sync_tracking`
- [x] Historical orders and Fills can be backfilled in batches
- [x] Historical backfill is explicitly enabled rather than performed on every normal startup
- [x] Backfill identifies unsuccessful records and supports an idempotent rerun

**Status: COMPLETE**

---

### 2. Historical Trading Reports and Metrics

As a commercial analyst, I want consistent reports by period, instrument, client, side and currency so that I can explain trading activity and compare it with earlier periods.

#### Acceptance Criteria

- [x] Reporting exposes order counts, filled/rejected outcomes, active clients and outcome rates
- [x] Execution reporting exposes Fill counts, executed quantity and traded value
- [x] BUY and SELL activity can be analysed separately
- [x] Instrument metrics include currency, Fill count, executed quantity and traded value
- [x] Client metrics include client identity, currency, order count and execution activity
- [x] Daily trends distinguish order activity from execution activity
- [x] Daily, Monday-based weekly and calendar-month reporting periods are supported
- [x] Previous-period comparisons use a defined baseline window
- [x] Internal reporting accepts inclusive date selections and converts them to a half-open query interval
- [x] Optional filters support instrument, side, status, client, account and currency at the API layer
- [x] Invalid date ranges and filter values receive validation errors
- [x] The Reports UI selects a single currency for monetary totals
- [x] Analytical aggregates use reporting repositories rather than live execution repositories
- [x] Reporting query methods are read-only

**Status: COMPLETE**

---

### 3. Analyst Reporting Interface

As Priya, I want to use a reporting interface backed by persisted records so that I can review business activity without requesting database extracts from engineering.

#### Acceptance Criteria

- [x] Authorized internal users can open the Reports section at `/analyst`
- [x] The interface loads backend reporting APIs rather than hard-coded dashboard figures
- [x] Reporting provides summary cards, business insights, trends, instrument/client breakdowns and an activity list
- [x] Date, instrument, BUY/SELL, status, client and currency filters are available
- [x] Apply and Reset actions reload the relevant reporting datasets
- [x] Currency choices are limited to USD, GBP and INR
- [x] Status choices are All statuses, FILLED and REJECTED
- [x] Summary cards and their comparison dialogs use the same server-provided current/previous snapshot
- [x] Displayed dates and currency describe the successfully applied filters
- [x] A failed replacement request does not relabel previously loaded data with unapplied filters
- [x] Newer filter requests supersede older requests
- [x] Loading, empty, invalid-filter, forbidden and service-error states are handled
- [x] Metric dialogs support keyboard focus, Escape and focus restoration
- [x] Charts support sparse dates, single-day ranges and readable date/rate labels

**Status: COMPLETE**

---

### 4. Operations Investigation Interface

As David, I want to find an order and inspect its persisted evidence so that I can answer a client dispute without asking engineering to reconstruct events from logs.

#### Acceptance Criteria

- [x] Operations and administrator users can open `/operations`
- [x] Analysts and ordinary clients are denied operations API access
- [x] Operations can search by order ID, client first/last name, account, instrument symbol, side, status and date range
- [x] The status filter offers All statuses, FILLED and REJECTED
- [x] An investigation displays available order, status-history, Fill, quote, cash and position evidence
- [x] The investigation includes trade-integrity verification
- [x] Incomplete evidence can be displayed alongside an integrity failure instead of being presented as a valid completed trade
- [x] Non-final SUBMITTED/ACCEPTED orders are identified as in progress when inspected
- [x] Instrument and account currencies are displayed with the relevant financial evidence
- [x] Execution quantities and prices preserve meaningful fractional precision
- [x] New selections cancel obsolete investigations and reject evidence for a different order ID
- [x] Search, investigation, retry, close, loading and error states are implemented
- [x] Investigation endpoints retrieve evidence and do not provide trade-editing or manual-settlement actions

**Status: COMPLETE**

---

### 5. Business Insights, Reconciliation and Reporting Validation

As an internal stakeholder, I want useful insights and evidence that reporting agrees with the source records so that I can trust the figures used in business decisions.

#### Acceptance Criteria

- [x] Business insights identify the most active instrument by Fill count
- [x] Growth ranking compares instrument activity against a non-zero previous-period baseline
- [x] Newly active instruments are distinguished from percentage growth
- [x] BUY/SELL distribution is calculated from Fill counts
- [x] Trading volume changes are compared with the previous reporting window
- [x] Client activity trends and the highest-volume trading day are available
- [x] Rejection trends include source order/rejection counts and percentage-point comparisons
- [x] Dates with no submitted orders produce missing rejection-rate observations rather than invented zero rates
- [x] The rejection chart preserves genuine 0% observations and breaks the line across missing observations
- [x] Current/previous business insights use one repeatable-read database snapshot
- [x] Business insights require one currency, whole-day boundaries and a maximum 366-day window
- [x] Integration tests reconcile filtered execution totals with persisted source Fills
- [x] Coverage includes empty periods, a single trade, multiple instruments, BUY/SELL and filled/rejected outcomes
- [x] Date-boundary, aggregation, duplicate-replay and reporting-refresh behavior are covered
- [x] Operational snapshots verify that reporting reads leave orders, Fills, balances, ledger, positions and history unchanged
- [x] Boundary tests check that analytical queries use reporting tables
- [x] Concurrent reporting/trading validation runs as an ordinary test during `mvn clean test`

**Status: COMPLETE**

---

## Business Requirement Coverage

| Requirement / Service Expectation | Sprint 6 Coverage |
|---|---|
| BR-16 — historical analysis without competing through the live trading query path | Reporting facts, indexed analytical queries, period/instrument/client filters, asynchronous synchronization and concurrent-load validation |
| BR-17 — useful internal business insights | Activity metrics, instrument rankings, client trends, BUY/SELL distribution, period comparisons and rejection trends |
| BR-14 / BR-15 — permanent trade evidence and reconstruction | Operations search and investigation expose the existing persisted audit and integrity services |
| BR-02 and Security and Confidentiality | Customer account isolation remains enforced; internal reporting and investigation require the appropriate roles |
| Trust and Continuity | Reporting reads are checked for operational non-mutation; synchronization replay and source reconciliation are tested |
| Usability | Role-based navigation, filter controls, meaningful metric labels, investigation states and accessible dialogs |

### Reporting Data and Query Boundaries

| Dataset / Path | Purpose | Time Basis |
|---|---|---|
| `reporting.order_facts` | Submitted-order population, current outcomes, rejection reasons and active clients | Order submission time |
| `reporting.trade_facts` | Successful execution counts, quantities, values and execution context | Fill execution time |
| `reporting.sync_tracking` | Trade synchronization attempt status and diagnostic information | Event/synchronization timestamps |
| Operations investigation | Source-record search, evidence retrieval and integrity checking for a selected order | Persisted lifecycle timestamps |

The reporting schema uses the same PostgreSQL database as the transactional schema. It separates the analytical dataset and query path; it is not a separate reporting database or read replica. Synchronization and backfill read source records to populate facts, and operations investigations deliberately read source evidence. Those paths are distinct from analytical aggregation.

Updates are eventually consistent because synchronization follows commit. Large backfills should be run deliberately, and acceptance-environment load measurements remain necessary because database and application resources are shared. The implementation and recorded load test do not establish zero resource contention at every scale.

### Metric Definitions and Period Comparisons

| Metric | Definition / Interpretation |
|---|---|
| Total / filled / rejected orders | Orders submitted inside the selected interval, classified by their stored reporting status |
| Successful trades / Fill count | Execution facts whose Fill time is inside the selected interval |
| Executed quantity | Sum of executed quantities, distinct from a count of orders or Fills |
| Traded value | Sum of execution value; the Reports UI and business insights filter to one currency |
| Most active instrument / peak trading day | Ranked by Fill count, not monetary value |
| BUY/SELL distribution | Share of execution Fill counts |
| Rejection rate | Rejected orders divided by submitted orders; rejection-rate changes are expressed in percentage points |
| Active clients | Distinct clients with qualifying order activity in the selected interval |

Filled-order and successful-trade counts can differ when an order is submitted in one period and executed in another. The interface retains those definitions rather than forcing unrelated populations to match.

Business-insight comparisons use an immediately preceding window with the same number of days. Calendar periodic reports compare with the preceding day, week or calendar month. A non-zero current value with a zero prior baseline has no defined percentage change; two empty periods are unchanged. Newly active instruments are reported separately from the fastest-growing instruments with an established baseline.

### Role-Based Access and Navigation

| Role | Sidebar Sections | Internal Reporting | Operations Investigation |
|---|---|---|---|
| Customer (`CLIENT`) | Dashboard | Denied | Denied |
| `ANALYST` | Dashboard, Reports | Allowed | Denied |
| `OPERATIONS` | Dashboard, Reports, Operations | Allowed | Allowed |
| `ADMIN` | Dashboard, Reports, Operations | Allowed | Allowed |

Frontend route guards and backend method authorization enforce access. Registration offers customer, analyst and operations roles; analyst and operations registration require the same internal access code. Administrator registration is not offered.

---

## Additional Sprint 6 Application Improvements

- [x] Registration explicitly selects an account currency from USD, GBP or INR
- [x] The backend validates the registration currency rather than silently assigning GBP
- [x] The customer dashboard provides modeled Add funds in the account's own currency
- [x] Funding validates positive amounts and currency precision, locks the authenticated account and records the resulting cash movement transactionally
- [x] An account-scoped funding request key makes retries idempotent and rejects conflicting reuse
- [x] The frontend retains an uncertain funding request for confirmation before another amount is submitted
- [x] Real payment/banking integration remains outside the delivered modeled-funding feature
- [x] INRUSD replaces USDJPY in the tradable FX universe
- [x] USDJPY is retired from new trading without rewriting historical orders, quotes, Fills or audit evidence
- [x] INRUSD is quoted in USD per INR; account-currency selection does not introduce automatic currency conversion
- [x] The browser's native password-reveal control is suppressed where it duplicates the application's reveal button
- [x] Reporting metric dialogs, rejection charts and investigation responses were corrected for consistency

New signup and Reports options are limited to the three supported UI currencies. Legacy currency definitions and persisted records are retained for historical integrity.

---

## Technical Deliverables

| Area | Main Deliverables |
|---|---|
| Reporting persistence | `TradeFact`, `OrderFact`, `SyncTracking` and reporting repositories |
| Synchronization | `ReportingDataSyncService`, `OrderFactEventListener`, `OrderFactSyncService` |
| Historical population | `TradeFactBackfillService`, `OrderFactBackfillService`, `ReportingBackfillRunner` |
| Metrics and reports | Trading activity, instrument/client metrics, daily trends, reporting activity and periodic reports |
| Business insights | `BusinessInsightsService`, `BusinessInsightsCalculator`, reporting windows and comparisons |
| Reporting APIs | `InternalReportingController`, `BusinessInsightsController`, `ReportingController`, validated query/filter models |
| Analyst frontend | `AnalystDashboardComponent`, typed reporting service, filters, charts and metric dialogs |
| Operations | Search service, protected controller, evidence/integrity response, Angular investigation interface |
| Account improvements | Role/currency registration, `AccountFundingService`, funding UI and request-key persistence |
| Test infrastructure | Shared test configuration, targeted fixture cleanup, valid lifecycle fixtures and batched load fixtures |
| CI | Frontend test/build stage, disposable PostgreSQL test database, backend verification and Surefire report publication |

### API Surface

| Method / Endpoint | Purpose | Authorized Internal Roles |
|---|---|---|
| `GET /api/internal/reporting/summary` | Order and execution summary | ADMIN, ANALYST, OPERATIONS |
| `GET /api/internal/reporting/activity` | Reporting activity list | ADMIN, ANALYST, OPERATIONS |
| `GET /api/internal/reporting/instruments` | Instrument aggregates | ADMIN, ANALYST, OPERATIONS |
| `GET /api/internal/reporting/clients` | Client aggregates | ADMIN, ANALYST, OPERATIONS |
| `GET /api/internal/reporting/trends` | Daily activity series | ADMIN, ANALYST, OPERATIONS |
| `GET /api/internal/reporting/insights` | Business insights and baseline comparison | ADMIN, ANALYST, OPERATIONS |
| `GET /api/reporting/trading`, `/instruments`, `/clients`, `/trends` | Period-based reporting APIs | ADMIN, ANALYST, OPERATIONS |
| `GET /api/reporting/periodic` | DAILY, WEEKLY or MONTHLY report | ADMIN, ANALYST, OPERATIONS |
| `GET /api/internal/operations/orders` | Operational order search | ADMIN, OPERATIONS |
| `GET /api/internal/operations/orders/{orderId}/audit` | Available evidence, integrity report and in-progress indicator | ADMIN, OPERATIONS |

The authenticated funding endpoint is `POST /api/accounts/me/funds`; account ownership is resolved from the signed-in user.

### Database Migrations

| Migration | Purpose |
|---|---|
| `V17__create_reporting_schema.sql` | Reporting schema, execution facts, synchronization tracking, source Fill lineage and reporting indexes |
| `V18__add_accepted_at_to_orders.sql` | Persist order acceptance time for lifecycle/reporting context |
| `V19__create_order_reporting_facts.sql` | Order reporting population, statuses, rejection reasons and indexes |
| `V20__account_funding_keys.sql` | Account-scoped funding-key uniqueness and modeled-funding credit constraint |
| `V21__replace_usdjpy_with_inrusd.sql` | Retire USDJPY and introduce tradable INRUSD while preserving audit history |

Historical reporting backfill is controlled by `app.reporting.backfill-enabled`, exposed as `REPORTING_BACKFILL_ENABLED`, and defaults to `false`.

---

## Testing and Verification

### Backend Coverage

| Coverage | Representative Tests |
|---|---|
| Synchronization, replay, aggregation, source reconciliation and non-mutation | `ReportingDataSyncIntegrationTest` |
| Historical order recovery without duplicate facts | `OrderFactBackfillIntegrationTest`, `OrderFactBackfillServiceTest` |
| Empty/filtered periods, currencies, counts and daily series | Trading activity, instrument, client, activity-trend and periodic reporting service tests |
| Rankings, zero baselines, insight validation and previous windows | `BusinessInsightsCalculatorTest`, `BusinessInsightsServiceTest` |
| Reporting query dependencies and SELECT-only analytical queries | `ReportingDataAccessBoundaryTest` |
| Role authorization and request validation | Reporting, internal reporting, business insights and operations controller security tests |
| Operational order filtering | `OperationsOrderSearchServiceTest` |

The reporting integration suite covers empty periods, individual trades, multiple instruments, filled/rejected orders, BUY/SELL, inclusive-start/exclusive-end boundaries, repeated refresh, status updates, source totals and operational-state snapshots.

### Recorded Sprint 6 Backend Results

The supplied delivery logs record two full Maven successes on 18 October 2026:

| Log | Finished At (UTC) | Tests | Failures | Errors | Skipped | Total Time |
|---|---|---:|---:|---:|---:|---|
| `test-output.txt` | 23:43:12 | 198 | 0 | 0 | 0 | 1 min 55 sec |
| `test-output(1).txt` | 23:56:03 | 198 | 0 | 0 | 0 | 1 min 42 sec |

The latter log also records **15 reporting integration tests passing with no skips**. These are dated Sprint 6 delivery results, not a claim that a later Sprint 7 checkout still contains exactly 198 backend tests.

### Concurrent Reporting and Trading Gate

`concurrentReportingRespectsTradingLatencyBudgetAndReconciles` is an ordinary `@Test`; it is not disabled or gated behind a separate opt-in profile.

Default settings are 200 seeded historical trades, three reporting workers, a 2,000 ms per-order budget and an allowed p95 slowdown factor of three. Settings can be adjusted using `goal5.seedTrades`, `goal5.reportWorkers`, `goal5.maxExecutionMs` and `goal5.allowedSlowdown`.

The test confirms reporting/trading overlap, checks query-worker completion, measures execution latency, applies absolute/relative limits and reconciles source/reporting totals after the load.

The final supplied log records:

| Measure | Recorded Value |
|---|---:|
| Seeded historical trades | 200 |
| Reporting workers | 3 |
| Completed reporting batches | 46 |
| Baseline p95 | 785 ms |
| Loaded p95 | 118 ms |
| Per-order budget | 2,000 ms |

These measurements demonstrate that this run passed its configured gate. The short sample and warm-up effects do not establish that reporting makes trading faster or prove production-scale capacity.

### Test Runtime and Fixture Improvements

- [x] Overlapping tests were consolidated while retaining distinct behavioral coverage
- [x] Reusable Spring test configuration reduces unnecessary context startup
- [x] Dedicated test database settings are supplied through one test profile
- [x] Background simulation, startup recovery and automatic backfill are disabled in the shared test profile unless a test enables the relevant behavior
- [x] Load-test history is seeded in batches rather than executing every historical trade through slow setup paths
- [x] Test-owned records are registered and cleaned up without deleting unrelated account data
- [x] Asynchronous reporting is awaited before fixture cleanup
- [x] New submitted/accepted fixtures receive valid initial lifecycle history
- [x] Missing SUBMITTED history in test fixtures was corrected without weakening production trade-integrity verification

The delivered optimization reduced the reported suite from approximately 200 tests and 11 minutes to 198 tests and about two minutes. The recorded 1 min 42 sec run confirms the improved runtime on that development environment; elapsed time varies by machine and database connection.

### Frontend Coverage

Frontend tests cover reporting filters and API parameters, backend-driven insights, server comparison windows, zero baselines, sparse rejection charts, genuine zero rates, applied-filter metadata, request races, dialog accessibility, operations searches, investigation selection/evidence consistency, currency precision, role navigation, registration and funding behavior.

Frontend test and production-build commands are included below. No new full-suite or Jenkins run is claimed for this documentation-only update. Existing Angular budget warnings are separate from functional test failures.

### Test Environment and Commands

Database-backed tests use the `test` profile and require `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` and `JWT_SECRET`. Fixture cleanup requires a dedicated database whose name ends in `_test`; the shared CI database is `leap_trading_test`. Earlier sprint documents describe the older dedicated database setup; the current test profile reads these environment variables.

From the repository root in PowerShell:

```powershell
Set-Location backend/trading-platform
.\mvnw.cmd clean test
```

For the frontend, from the repository root:

```powershell
Set-Location frontend
npm ci
npm test -- --watch=false
npm run build
```

### Jenkins Configuration

The Jenkinsfile defines frontend dependency installation, tests and production build in Node 24; an ephemeral PostgreSQL 17 database; Java 21/Maven backend `clean verify`; JUnit/Surefire report publication; and database-container cleanup. Database and JWT secrets are obtained through Jenkins credentials.

The pipeline publishes test reports instead of accepting missing reports as a passing test run. A successful Jenkins run for the reviewed branch must be recorded separately; pipeline configuration alone is not evidence that that run passed.

---

## Definition of Done

Sprint 6's functional delivery is complete when:

1. [x] Committed trading activity populates distinct order and execution reporting datasets
2. [x] Historical reporting data can be populated without duplicate facts
3. [x] Analysts can query periods, instruments, clients, sides, outcomes and currencies through protected reporting APIs
4. [x] The Reports UI displays persisted metrics, useful insights and defined comparison windows
5. [x] Operations can find an order and inspect its available lifecycle and integrity evidence
6. [x] Customers, analysts and operations users receive the intended navigation and API permissions
7. [x] Order, Fill, quantity, value and rejection-rate definitions are explicit and consistently presented
8. [x] Reporting totals are tested against source records for the same filters and time basis
9. [x] Reporting reads are tested not to modify operational trading data
10. [x] Boundary tests keep analytical aggregation on the reporting path
11. [x] Concurrent reporting/trading validation runs in the normal Maven suite
12. [x] The final supplied Sprint 6 Maven run passes without skipped tests

The dated backend evidence above records the completed test run. Current-branch frontend/build and Jenkins results should accompany the final release evidence rather than being inferred from the implementation checklist.

---

## Sprint Outcome

Sprint 6 provides internal stakeholders with reporting derived from committed trading records and gives operations a practical interface for investigating client trades. Priya can analyse historical activity, instrument rankings, client behavior, period changes and rejection trends. David can search for an order, inspect its persisted evidence and review its integrity result.

The reporting path separates analytical reads from live execution repositories, synchronizes facts after commit, supports controlled historical backfill and includes source-reconciliation, non-mutation and concurrent-load verification. Supporting changes improve role navigation, account currency selection, modeled funding, currency consistency and test runtime.

---

## Implementation References

- [Sprint 5 — audit, recovery and settlement foundation](sprint-5.md)
- [Reporting backend](../backend/trading-platform/src/main/java/com/goatteen/trading/reporting)
- [Operations backend](../backend/trading-platform/src/main/java/com/goatteen/trading/operations)
- [Analyst reporting interface](../frontend/src/app/analyst)
- [Operations interface](../frontend/src/app/operations)
- [Reporting integration tests](../backend/trading-platform/src/test/java/com/goatteen/trading/reporting/ReportingDataSyncIntegrationTest.java)
- [Reporting data-access boundary tests](../backend/trading-platform/src/test/java/com/goatteen/trading/reporting/ReportingDataAccessBoundaryTest.java)
- [Shared integration-test configuration](../backend/trading-platform/src/test/resources/application-test.yaml)
- [Database migrations](../backend/trading-platform/src/main/resources/db/migration)
- [Jenkins pipeline](../Jenkinsfile)
- [Development environment setup](../README.md)

---

## Status

**COMPLETE**

*Sprint 6 functional delivery is complete. Recorded test results are dated delivery evidence; later Sprint 7 features and release-readiness work are tracked separately.*
