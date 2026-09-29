# Sprint 4

## Sprint Goal

Implement accepted-order execution, atomic trade settlement, portfolio updates, real-time trade status, and comprehensive execution/settlement verification for the LEAP trading platform.

**Sprint Goal Status: COMPLETE**

---

## User Stories

### 1. Order Execution and Lifecycle Completion

As a client, I want my accepted BUY and SELL orders to execute against current market quotes so that successfully validated orders become completed trades.

#### Acceptance Criteria

- [x] Valid orders progress through `SUBMITTED → ACCEPTED → FILLED`
- [x] Invalid orders progress through `SUBMITTED → REJECTED` with a stored rejection reason
- [x] Execution is permitted only for orders in the `ACCEPTED` state
- [x] BUY orders execute using the latest available ask price
- [x] SELL orders execute using the latest available bid price
- [x] Executed orders create a Fill recording the executed price, quantity, and timestamp
- [x] Every Fill references its originating order and the market quote used for execution
- [x] A successfully executed order receives a `FILLED` status and completion timestamp
- [x] Order status changes are recorded in the order status history
- [x] Already-filled orders cannot be executed again
- [x] Execution rechecks instrument/account currency compatibility and does not perform implicit currency conversion

**Status: COMPLETE**

---

### 2. Atomic Trade Settlement and Audit Trail

As a client, I want my executed trades to update cash, positions, and transaction records together so that my portfolio remains consistent even if execution fails.

#### Acceptance Criteria

- [x] Fill creation, cash movement, position movement, cash ledger recording, and final order status occur within one `@Transactional` execution operation
- [x] BUY settlement deducts the execution cost from the client's cash balance
- [x] SELL settlement credits the execution proceeds to the client's cash balance
- [x] Each successful execution creates one cash transaction with the signed amount and resulting balance
- [x] Each trade-related cash transaction references its corresponding Fill
- [x] Each Fill retains the market quote used to determine the execution price
- [x] Settlement failures roll back the cash, ledger, position, Fill, and `FILLED` status changes together
- [x] An order whose execution transaction fails remains `ACCEPTED` when acceptance was committed beforehand
- [x] Insufficient cash or holdings prevent settlement
- [x] Database constraints prevent negative cash balances and negative position quantities

**Status: COMPLETE**

---

### 3. Portfolio Updates and Account Isolation

As a client, I want completed trades to update my cash balance and holdings accurately without affecting another client's portfolio.

#### Acceptance Criteria

- [x] BUY execution reduces the purchasing account's cash balance
- [x] BUY execution increases an existing position or creates a position for a first purchase
- [x] SELL execution increases the selling account's cash balance
- [x] SELL execution reduces the existing instrument position
- [x] Holdings are checked again at execution time so an intervening trade cannot cause an invalid sale
- [x] Cash cannot become negative through execution
- [x] Position quantities cannot become negative through execution
- [x] Settlement updates are scoped to the account that owns the order
- [x] Execution for one account does not change another account's cash, positions, or ledger
- [x] Account and position optimistic locking protect concurrent updates
- [x] Concurrent settlement tests verify that overlapping BUY attempts cannot overspend the account

**Status: COMPLETE**

---

### 4. Real-Time Trade Status and Portfolio Refresh

As a client, I want to see completed orders and updated portfolio information on the dashboard without manually reloading the page.

#### Acceptance Criteria

- [x] Successful order submission returns a `FILLED` order response
- [x] The dashboard displays completed orders with `FILLED` status
- [x] Filled orders show their execution price and execution timestamp
- [x] The order API and account blotter expose Fill price and the actual Fill execution timestamp
- [x] The dashboard refreshes account cash, holdings, and recent orders after successful execution
- [x] The dashboard automatically polls account and portfolio information every three seconds
- [x] Market-data and portfolio polling operate independently
- [x] Background refreshes do not repeatedly display the main loading state
- [x] Previously loaded portfolio information is retained during temporary background refresh failures
- [x] Polling intervals are cleaned up when the dashboard component is destroyed
- [x] Frontend tests verify completed-order display, portfolio refresh, and automatic refresh without manual reload or new order submission

**Status: COMPLETE**

---

### 5. Execution, Settlement, and Auditability Verification

As a development team, we want automated tests of successful trades, failed trades, concurrency, and client updates so that the execution and settlement behavior can be verified before delivery.

#### Acceptance Criteria

- [x] Backend controller tests verify the transition from accepted to filled orders
- [x] Order validation tests cover BUY and SELL requirements, including currency compatibility
- [x] BUY execution tests verify ask pricing, cash deduction, new position creation, Fill creation, and cash ledger recording
- [x] SELL execution tests verify bid pricing, cash credit, position reduction, Fill creation, and cash ledger recording
- [x] Tests verify that the cash ledger entry references the corresponding Fill
- [x] Tests cover insufficient cash and insufficient holdings at execution time
- [x] Tests verify that an already-filled order cannot execute again
- [x] PostgreSQL integration testing proves that flushed cash, ledger, and position changes roll back when Fill creation fails
- [x] PostgreSQL tests verify that rejection has no settlement effects
- [x] PostgreSQL tests verify that duplicate execution cannot create a second Fill or ledger entry
- [x] PostgreSQL tests verify account isolation
- [x] Concurrent execution tests verify that two orders cannot collectively overspend one account
- [x] Controlled-overlap concurrency testing exercises optimistic locking after both transactions read the same account version
- [x] Frontend tests verify `FILLED` status, price, execution time, refreshed portfolio information, and three-second polling
- [x] Complete backend and frontend test suites pass

**Status: COMPLETE**

---

## Business Requirement Execution and Settlement Coverage

Sprint 4 extends Sprint 3's order-entry functionality into completed trading and portfolio settlement.

| Area | Implemented Behavior |
|---|---|
| Successful lifecycle | `SUBMITTED → ACCEPTED → FILLED` |
| Rejected lifecycle | `SUBMITTED → REJECTED` |
| BUY execution price | Latest available ask price |
| SELL execution price | Latest available bid price |
| Fill auditability | Fill references order and actual execution quote |
| Cash auditability | Signed cash ledger entry references the corresponding Fill |
| BUY settlement | Cash decreases; holdings increase |
| SELL settlement | Cash increases; holdings decrease |
| Transaction boundary | Cash, position, ledger, Fill, and final status are settled atomically |
| Execution failure | Settlement rolls back; previously accepted order remains `ACCEPTED` |
| Concurrency | Account/position optimistic locking and database integrity constraints |
| Portfolio display | Immediate post-trade refresh and automatic three-second polling |

### Execution Lifecycle

A submitted order is persisted and validated. Invalid orders are rejected with a reason and do not enter settlement. Valid orders are accepted, then executed against the latest relevant market quote. Successful settlement creates a Fill and cash transaction, updates the portfolio, records `FILLED` history, and completes the order.

Submission and acceptance are separate from the execution transaction. This distinction allows a failed execution to roll back its settlement without erasing a previously committed `ACCEPTED` order.

### Currency Handling

Cross-currency conversion is not implemented in Sprint 4. Account and instrument currencies must match for settlement; compatibility is checked during validation and again at execution time rather than assuming an exchange rate.

---

## Technical Deliverables

- [x] Accepted-order execution in the Spring Boot trading backend
- [x] Latest market-quote lookup during execution
- [x] Side-specific ask/bid execution pricing
- [x] Fill persistence with order, quote, price, quantity, and execution time
- [x] `SUBMITTED`, `ACCEPTED`, `REJECTED`, and `FILLED` status-history handling
- [x] Single-transaction execution and settlement with Spring `@Transactional`
- [x] BUY and SELL cash-balance mutation
- [x] Signed cash ledger entries with post-transaction balance
- [x] Direct linkage from each trade-related cash transaction to its Fill
- [x] Position creation for first-time BUY orders
- [x] Position increases and decreases for BUY and SELL orders
- [x] Execution-time cash and holdings safeguards
- [x] Account and position optimistic-locking support
- [x] Database constraints for non-negative position quantities and cash balances
- [x] `V12__enforce_non_negative_cash.sql` migration
- [x] Filled-order response fields for execution price and Fill execution time
- [x] Angular immediate account, holdings, and blotter refresh after completed trades
- [x] Independent three-second dashboard and market refresh intervals
- [x] Automatic-refresh cleanup and silent background-loading behavior
- [x] Updated order-controller and order-validation tests
- [x] BUY and SELL order-execution unit tests
- [x] Insufficient-balance, insufficient-holdings, and duplicate-execution unit tests
- [x] Database-backed rollback integration test
- [x] Account-isolation integration test
- [x] Concurrent-execution and controlled optimistic-locking integration tests
- [x] Database-backed rejection and duplicate-execution lifecycle tests
- [x] Angular filled-order, portfolio-refresh, and automatic-polling tests

---

## Testing and CI Results

### Backend

Spring Boot/Maven verification was reported successful:

- **25 backend tests passed**
- Updated order-controller tests verify execution through `FILLED`
- Updated validation tests cover BUY/SELL currency behavior
- Execution-service tests cover successful BUY/SELL settlement and failed-execution scenarios
- PostgreSQL rollback testing confirms atomic settlement after a deliberately injected failure
- PostgreSQL account-isolation testing confirms that other accounts are unaffected
- Concurrent-order and controlled-overlap tests exercise overspending prevention and optimistic locking
- Database-backed lifecycle tests cover rejection and duplicate-execution safeguards
- Fill-to-cash-ledger linkage is checked by tests

### Frontend

Angular/Vitest verification was reported successful:

- **23 frontend tests passed**
- Dashboard tests verify filled-order status, price, and execution time
- Portfolio-refresh testing verifies updated cash, holdings, and recent orders following a simulated completed BUY
- Automatic-polling testing verifies the three-second refresh without manual reload or a newly submitted order
- Previously existing frontend tests remained passing after the Sprint 4 updates

### PostgreSQL Integration Test Environment

- A dedicated local database, `leap_sprint4_rollback_test`, was created for database-backed verification
- Tests use the dedicated `leap_sprint4_test` database user and password supplied through `LEAP_TEST_DB_PASSWORD`
- Integration tests include checks against inadvertently using an unexpected database
- The isolated PostgreSQL database was used for rollback, lifecycle, account-isolation, and concurrency testing

### Build and CI Verification

- The complete backend and frontend test counts above were reported after the final Sprint 4 verification checkpoint
- Angular production build and Jenkins CI completion have **not been separately confirmed in the reported results**; record their outcomes when available rather than treating local test success as CI verification

---

## Definition of Done

Sprint 4 is functionally complete when a client can:

1. [x] Submit an authenticated BUY or SELL order
2. [x] Receive a stored acceptance or rejection decision
3. [x] Have an accepted order execute at the relevant current market price
4. [x] Receive a completed order with `FILLED` status, execution price, and execution time
5. [x] Have a Fill that references the originating order and executed market quote
6. [x] Have cash and holdings updated correctly after each completed trade
7. [x] Have each trade-related cash movement recorded and linked to its Fill
8. [x] Avoid negative cash or position balances and unsupported currency conversion
9. [x] Have execution failures roll back settlement without partially completing the order
10. [x] Have concurrent trade attempts protected against conflicting portfolio updates
11. [x] View refreshed cash, holdings, and recent orders without manually reloading the dashboard
12. [x] Access functionality supported by backend, frontend, and PostgreSQL integration tests

Additional verification:

- [x] **25 backend tests passed**
- [x] **23 frontend tests passed**
- [x] PostgreSQL rollback, account-isolation, lifecycle, and concurrency integration tests passed
- [x] Frontend automatic-polling test passed
- [ ] Record final Angular production build result if not already captured elsewhere
- [ ] Record Jenkins CI result after the Sprint 4 branch is built in CI
- [ ] Confirm the final Sprint 4 commit, push, and merge or pull-request status

---

## Sprint Outcome

Sprint 4 completes the execution and settlement layer of the LEAP trading platform, building on Sprint 3's simulated market data, authenticated trading dashboard, and validated order submission.

Accepted orders now execute against the latest available market quote, using the ask price for purchases and the bid price for sales. Completed trades create auditable Fills that identify their source orders and quotes. Cash transactions record the signed settlement amounts, resulting balances, and associated Fills.

The backend applies cash, position, ledger, Fill, and final order-status changes in one execution transaction. When execution fails, its settlement changes are rolled back while previously committed acceptance remains intact. Account and position safeguards prevent negative balances, and PostgreSQL tests cover account isolation and concurrent execution conflicts.

The Angular dashboard presents completed orders with fill price and execution time, immediately refreshes account and portfolio data after a successful trade, and polls for updates every three seconds without requiring a manual reload.

Final Sprint 4 test verification was reported as **25 passing backend tests and 23 passing frontend tests**, including database-backed rollback, account-isolation, lifecycle, concurrency, and frontend refresh checks. Production-build and Jenkins results should be recorded separately when confirmed.

---

## Status

**COMPLETE**

*Functional scope and reported automated-test checkpoints are complete; final CI and branch-delivery evidence is tracked separately above.*
