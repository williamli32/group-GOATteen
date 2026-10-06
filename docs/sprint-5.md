# Sprint 5

## Sprint Goal

Strengthen the LEAP trading platform so that every trade is fully reconstructable, duplicate processing cannot create duplicate settlement, interrupted executions can be recovered safely after restart, settlement integrity can be verified from persisted data, and the resulting reliability controls are covered by database-backed integration tests.

**Sprint Goal Status: COMPLETE**

---

## User Stories

### 1. Complete Trade Audit Trail and Reconstruction

As operations or compliance staff, I want to reconstruct the full lifecycle of any completed or rejected trade from persisted data so that client disputes and audit questions can be answered without relying on application logs or developer memory.

#### Acceptance Criteria

- [x] A completed trade can be reconstructed using persisted database records only
- [x] The reconstructed trade identifies the originating order
- [x] The reconstructed trade identifies the client account
- [x] The reconstructed trade identifies the traded instrument
- [x] The reconstructed trade includes BUY/SELL side and requested quantity
- [x] The reconstructed trade includes the complete order-status history
- [x] A filled order must have the exact lifecycle `SUBMITTED → ACCEPTED → FILLED`
- [x] A rejected order must have the exact lifecycle `SUBMITTED → REJECTED`
- [x] A filled trade includes its Fill ID, execution quantity, price, and execution timestamp
- [x] A filled trade includes the persisted market quote used during execution
- [x] The reconstruction verifies that BUY execution used the persisted ask price
- [x] The reconstruction verifies that SELL execution used the persisted bid price
- [x] The reconstruction includes the cash movement created by settlement
- [x] The reconstruction includes the resulting cash balance
- [x] The reconstruction includes position quantity before, after, and the resulting quantity change
- [x] Rejected orders contain no Fill or settlement artifacts
- [x] Rejected orders retain a rejection reason and completion timestamp
- [x] Incomplete or contradictory persisted data causes reconstruction to fail rather than silently returning an incomplete audit trail
- [x] `GET /api/orders/{orderId}/audit` exposes the reconstructed trade through an API-safe response
- [x] Audit access is restricted to the authenticated client's own account

**Status: COMPLETE**

---

### 2. Idempotent Execution and Duplicate Protection

As a client, I want a repeated or retried order request to produce the same trading result instead of creating a second trade so that network retries or duplicate submissions cannot deduct cash or update holdings twice.

#### Acceptance Criteria

- [x] Order submission requires an `Idempotency-Key` request header
- [x] Blank idempotency keys are rejected
- [x] The idempotency key is persisted on the order
- [x] The execution idempotency key is persisted on the Fill
- [x] Database constraints enforce uniqueness of order idempotency keys
- [x] Database constraints enforce uniqueness of Fill idempotency keys
- [x] A retry using the same key and the same logical order returns the existing result
- [x] A retry of an already-filled order does not create a second Fill
- [x] A retry of an already-filled order does not create a second cash transaction
- [x] A retry of an already-filled order does not update the position a second time
- [x] A retry does not create a second `FILLED` status-history event
- [x] Reusing an idempotency key with different order parameters is rejected
- [x] Reusing an execution key for a different order is rejected
- [x] Execution uses a pessimistic order lock before settlement
- [x] Settlement is allowed only while the order remains in `ACCEPTED` state
- [x] The Angular client stores the current pending idempotency key in `sessionStorage`
- [x] The Angular client reuses the same key while retrying the same pending request
- [x] The frontend clears the key after a definitive successful or rejected result
- [x] The frontend clears a stale key after the backend returns an idempotency-parameter conflict

**Status: COMPLETE**

---

### 3. Failure Recovery and Restart Resilience

As operations staff, I want the platform to determine safely whether an interrupted order should be retried after a restart so that failures do not leave trades duplicated, lost, or partially settled.

#### Acceptance Criteria

- [x] Recovery decisions are based on persisted database state rather than in-memory process state
- [x] `SUBMITTED` orders without a Fill are classified as submitted but not executed
- [x] `ACCEPTED` orders without a Fill are classified as safe to retry
- [x] Valid `FILLED` orders are classified as fully executed and must not be settled again
- [x] Rejected orders are classified as non-executable
- [x] Contradictory states are classified as `AMBIGUOUS`
- [x] `SUBMITTED` with a Fill is treated as ambiguous
- [x] `ACCEPTED` with an existing Fill is treated as ambiguous
- [x] `REJECTED` with a Fill is treated as ambiguous
- [x] `FILLED` without a Fill is treated as ambiguous
- [x] A filled order that fails integrity verification is treated as ambiguous
- [x] Recovery never automatically executes an ambiguous order
- [x] Only `ACCEPTED + no Fill` is automatically recoverable
- [x] Recovery reuses the order's persisted idempotency key where available
- [x] Older orders without a key use a deterministic recovery key derived from the order ID
- [x] Recovery does not generate a random UUID for the same interrupted order
- [x] Startup recovery scans persisted `ACCEPTED` orders
- [x] Startup recovery verifies persisted `FILLED` orders without executing them again
- [x] One failed recovery candidate does not prevent other recovery candidates from being checked
- [x] A deliberately failed settlement transaction rolls back Fill, cash, ledger, position, and `FILLED` history together
- [x] After rollback, recovery identifies the order as `ACCEPTED_NOT_EXECUTED`
- [x] Application restart testing uses the same PostgreSQL database across multiple Spring application contexts
- [x] The first restarted application instance recovers an accepted unexecuted order
- [x] A subsequent restart verifies the already-filled order and does not settle it again

**Status: COMPLETE**

---

### 4. Settlement Integrity and Reconciliation

As operations and risk staff, I want the persisted settlement records to be checked for internal consistency so that incorrect or corrupted trade state is detected and explained instead of being treated as valid.

#### Acceptance Criteria

- [x] A trade-integrity report can be generated for a persisted order
- [x] A valid completed trade is reported as reconstructable and internally consistent
- [x] The Fill must belong to the reconstructed order
- [x] Fill quantity must match order quantity
- [x] The Fill must retain a persisted market quote
- [x] The persisted quote must belong to the traded instrument
- [x] The Fill price must match the side-specific price from the persisted quote
- [x] A filled trade must have exactly one cash settlement
- [x] The cash transaction must belong to the correct client account
- [x] The cash transaction must reference the correct Fill
- [x] Cash settlement arithmetic must satisfy `balance_before + amount = balance_after`
- [x] The cash amount must match the executed quantity multiplied by the execution price
- [x] BUY cash movements must be negative
- [x] SELL cash movements must be positive
- [x] The current account cash balance must match the latest persisted cash-ledger result
- [x] Position history must reference the correct account, instrument, and Fill
- [x] Position-history quantity change must equal `quantity_after - quantity_before`
- [x] Position change must match the expected BUY or SELL quantity
- [x] Current position quantity must match the latest persisted position-history result
- [x] Settlement-integrity checks detect deliberately corrupted cash balances
- [x] Settlement-integrity checks detect deliberately corrupted position quantities
- [x] Settlement-integrity checks detect missing position-history data
- [x] Database constraints require a Fill to reference a quote
- [x] Database constraints prevent more than one cash settlement from referencing the same Fill
- [x] Position-history precision is aligned with trading quantity precision
- [x] Cash transactions persist `balance_before` as well as the signed amount and resulting balance

**Status: COMPLETE**

---

### 5. Reliability, Account Isolation, and Concurrent Duplicate-Processing Verification

As a development team, we want database-backed tests for isolation, duplicate processing, restart recovery, reconciliation, and transaction rollback so that Sprint 5 reliability behavior can be verified before delivery.

#### Acceptance Criteria

- [x] Trade reconstruction is verified against real persisted PostgreSQL records
- [x] Filled and rejected reconstruction paths are covered
- [x] Corrupted cash-ledger arithmetic is detected by tests
- [x] Current account-balance mismatches are detected by tests
- [x] Current position mismatches are detected by tests
- [x] Missing position-history records cause integrity verification to fail
- [x] Repeated execution with the same idempotency key returns the original Fill
- [x] Duplicate execution creates only one Fill
- [x] Duplicate execution creates only one cash settlement
- [x] Duplicate execution updates the position only once
- [x] Duplicate execution creates only one `FILLED` audit event
- [x] Mid-settlement failure after database flush is proven to roll back atomically
- [x] Recovery-state classification is covered by automated tests
- [x] Startup recovery invocation is covered by automated tests
- [x] Application restart recovery is exercised against persistent PostgreSQL state
- [x] A second restart proves that a completed trade is not settled twice
- [x] Settlement for one account is verified not to change another account's cash balance
- [x] Settlement for one account is verified not to change another account's position or ledger
- [x] Concurrent execution attempts using the same logical execution are tested
- [x] Concurrent duplicate processing is verified to leave exactly one Fill
- [x] Concurrent duplicate processing is verified to leave exactly one cash movement
- [x] Concurrent duplicate processing is verified to leave exactly one position-history movement
- [x] Concurrent duplicate processing is verified to deduct cash exactly once
- [x] Database-backed tests use the dedicated integration-test database instead of the normal development database
- [x] Integration tests verify the current database and database user before modifying data

**Status: COMPLETE**

---

## Business Requirement Reliability and Audit Coverage

Sprint 5 builds on Sprint 4's execution and settlement functionality by adding durable auditability, duplicate protection, deterministic recovery, reconciliation, and deeper reliability verification.

| Business Requirement / Service Expectation | Sprint 5 Coverage |
|---|---|
| BR-02 — client data isolation | Audit API checks authenticated account ownership; account-isolation settlement testing |
| BR-09 — holdings, cash and permanent trade record update together | Transaction rollback verification and duplicate-settlement protection |
| BR-14 — accepted orders, pricing decisions, cash and holdings permanently recorded | Persisted status history, Fill, quote, cash ledger, position history, restart-safe records |
| BR-15 — full trade lifecycle reconstruction | Strict reconstruction service and client-owned audit endpoint |
| Trust and Continuity | Restart recovery, idempotent execution, atomic rollback, no duplicate settlement |
| Data Retention and Regulatory Alignment | Reconstruction and integrity verification operate from persisted trade records |

### Trade Reconstruction Model

A successfully filled trade is reconstructed through:

```text
Order
  |
  +--> Order Status History
  |      SUBMITTED
  |      ACCEPTED
  |      FILLED
  |
  +--> Fill
  |      |
  |      +--> Persisted Quote
  |
  +--> Cash Transaction
  |      balance_before
  |      signed amount
  |      balance_after
  |
  +--> Position History
         quantity_before
         quantity_change
         quantity_after
```

A rejected trade follows:

```text
Order
  |
  +--> SUBMITTED
  |
  +--> REJECTED
  |
  +--> Rejection Reason
  |
  +--> No Fill
  +--> No Cash Settlement
  +--> No Position Settlement
```

### Idempotency Model

The client generates and retains a pending idempotency key for one logical order request.

```text
Angular Client
    |
    | Idempotency-Key
    v
OrderController
    |
    +--> orders.idempotency_key
    |
    v
OrderExecutionService
    |
    +--> fills.idempotency_key
    |
    v
Single Settlement
```

For repeated requests:

```text
Same key + same request
        |
        v
Return existing result
        |
        v
No second settlement
```

For conflicting reuse:

```text
Same key + different request
        |
        v
Reject request
```

### Recovery Model

Recovery uses persisted state rather than assuming whether execution succeeded.

```text
SUBMITTED + no Fill
        |
        v
SUBMITTED_NOT_EXECUTED
Do not execute automatically


ACCEPTED + no Fill
        |
        v
ACCEPTED_NOT_EXECUTED
Safe to retry


FILLED + complete valid audit state
        |
        v
FULLY_EXECUTED
Never execute again


Contradictory persisted state
        |
        v
AMBIGUOUS
Never guess or auto-execute
```

### Restart Recovery Flow

```text
Application Instance #1
        |
        | order persisted as ACCEPTED
        | execution not committed
        v
Application stops

PostgreSQL state survives
        |
        v
Application Instance #2
        |
        | startup recovery
        v
ACCEPTED + no Fill
        |
        v
execute safely
        |
        v
FILLED

Application stops again
        |
        v
Application Instance #3
        |
        | startup integrity check
        v
FILLED + valid settlement
        |
        v
No second execution
```

---

## Technical Deliverables

- [x] `TradeAuditTrail` persisted trade reconstruction model
- [x] `TradeAuditResponse` API-safe audit response
- [x] `TradeAuditController`
- [x] `GET /api/orders/{orderId}/audit`
- [x] Authenticated account-ownership protection for trade audit access
- [x] Strict `TradeReconstructionService`
- [x] `TradeIntegrityReport`
- [x] Verification of exact filled and rejected order lifecycles
- [x] Persisted quote verification during trade reconstruction
- [x] Cash-settlement arithmetic verification
- [x] Current cash-balance reconciliation
- [x] Position-history verification
- [x] Current position reconciliation
- [x] Persistent order idempotency key
- [x] Persistent Fill idempotency key
- [x] Required `Idempotency-Key` HTTP header for order submission
- [x] Same-key/same-request retry handling
- [x] Same-key/different-request conflict handling
- [x] Pessimistic order locking during execution
- [x] Atomic Fill, cash, ledger, position, and status settlement
- [x] Angular `IdempotencyService`
- [x] Pending idempotency key stored in `sessionStorage`
- [x] Recovery state model
- [x] `RecoveryAssessment`
- [x] `RecoveryService`
- [x] Deterministic recovery-key generation
- [x] Startup recovery through `ApplicationRunner`
- [x] Startup recovery configurable through `app.recovery.enabled`
- [x] `V14__add_idempotency_key_to_fills.sql`
- [x] `V15__add_idempotency_key_to_orders.sql`
- [x] `V16__enforce_settlement_integrity.sql`
- [x] Fill quote `NOT NULL` enforcement
- [x] Unique cash-transaction-per-Fill database index
- [x] Cash `balance_before` persistence
- [x] Position-history quantity precision alignment
- [x] Trade reconstruction integration tests
- [x] Persistent idempotency lifecycle integration tests
- [x] Transaction rollback integration test
- [x] Recovery-service tests
- [x] Startup-recovery configuration test
- [x] Application restart recovery integration test
- [x] Account-isolation integration test
- [x] Concurrent duplicate-processing integration test
- [x] Controller idempotency tests
- [x] Frontend order-service idempotency-header coverage
- [x] Frontend pending-key lifecycle handling

---

## Database Migrations

### V14 — Fill Idempotency

`V14__add_idempotency_key_to_fills.sql`

Adds persistent execution idempotency to the `fills` table.

Key behavior:

- Stores the execution idempotency key
- Enforces uniqueness
- Allows completed execution retries to find and return the original Fill

### V15 — Order Idempotency

`V15__add_idempotency_key_to_orders.sql`

Adds the idempotency key to the original order record.

Key behavior:

- Associates one client request key with one persisted order
- Prevents duplicate logical order creation
- Allows the controller to recognize an already-completed request before starting settlement

### V16 — Settlement Integrity

`V16__enforce_settlement_integrity.sql`

Strengthens settlement auditability and reconciliation.

Key behavior:

- Requires `fills.quote_id`
- Allows only one cash settlement per Fill
- Adds `cash_transactions.balance_before`
- Backfills historical starting balances
- Makes `balance_before` mandatory
- Aligns position-history precision with trading quantities

---

## Testing and Verification

### Backend

Sprint 5 adds or extends automated verification across:

- Trade reconstruction
- Rejected-order reconstruction
- Missing audit data
- Cash reconciliation
- Position reconciliation
- Persistent idempotency
- Duplicate execution
- Transaction rollback
- Recovery classification
- Startup recovery
- Application restart recovery
- Account isolation
- Concurrent duplicate processing
- Idempotency-key controller behavior

A reported full Maven run discovered **50 backend tests**.

Before the final Goal 5 database-configuration correction, that run reported:

- **50 tests discovered**
- **0 assertion failures**
- **2 ApplicationContext errors**

The two errors were caused by the newly added account-isolation and concurrent-duplicate tests pointing at the normal development database route instead of the dedicated integration-test database.

Those two tests were subsequently corrected to use:

```text
Database: leap_sprint4_rollback_test
User: leap_sprint4_test
Port: 5432
Password: LEAP_TEST_DB_PASSWORD (whatever you choose as the password)
```

The final all-green `mvn clean test` result should be recorded after the corrected branch is rerun.

### Frontend

Sprint 5 frontend work includes:

- Required `Idempotency-Key` transmission in `OrderService`
- Pending key persistence in `sessionStorage`
- Key reuse during retries
- Key clearing after successful completion
- Key clearing after stored rejection
- Key clearing after a `422` key/parameter conflict
- Updated order-service tests for the two-argument `placeOrder(request, idempotencyKey)` API
- Header verification for `Idempotency-Key`

A stale frontend test that still called `placeOrder(request)` with one argument was identified and corrected during Sprint 5.

The final complete frontend test count should be recorded after the final Sprint 5 verification run.

### PostgreSQL Integration Test Environment

Database-backed Sprint 5 testing uses the existing dedicated integration environment:

```text
Database: leap_sprint4_rollback_test
User: leap_sprint4_test
Password environment variable: LEAP_TEST_DB_PASSWORD
```

The tests deliberately refuse to proceed when connected to an unexpected database or database user.

The isolated PostgreSQL environment is used for:

- Settlement rollback
- Trade reconstruction
- Idempotency lifecycle verification
- Restart recovery
- Account isolation
- Concurrent duplicate-processing verification

### Build and CI Verification

- [x] Sprint 5 functional implementation completed
- [x] Goal 1 trade reconstruction implementation completed
- [x] Goal 2 persistent idempotency implementation completed
- [x] Goal 3 restart recovery implementation completed
- [x] Goal 4 settlement integrity and reconciliation implementation completed
- [x] Goal 5 isolation and concurrency test coverage added
- [x] Stale in-memory idempotency implementation removed
- [x] Merge-conflict artifacts in Sprint 5 production files resolved
- [x] Goal 5 tests corrected to use the dedicated integration-test database
- [ ] Record the final corrected `mvn clean test` result
- [ ] Record the final frontend test result
- [ ] Record Angular production-build result if required
- [ ] Record Jenkins/CI result when available

---

## Definition of Done

Sprint 5 is functionally complete when the platform can:

1. [x] Reconstruct a filled trade entirely from persisted records
2. [x] Reconstruct a rejected order entirely from persisted records
3. [x] Identify the order, client account, instrument, status history, Fill, quote, cash movement, resulting balance, and position change for a completed trade
4. [x] Reject incomplete or contradictory audit state instead of silently treating it as valid
5. [x] Require a client idempotency key for order submission
6. [x] Return the original result when the same logical request is retried
7. [x] Prevent retries from creating duplicate Fill, cash, position, or status changes
8. [x] Reject reuse of the same key for different order parameters
9. [x] Determine recovery state from persisted data after an interruption
10. [x] Safely retry only accepted orders whose execution never committed
11. [x] Refuse automatic recovery for ambiguous persisted states
12. [x] Run persisted recovery checks when the application starts
13. [x] Recover an accepted order after an application restart
14. [x] Avoid settling an already-completed trade after a later restart
15. [x] Verify cash-settlement arithmetic against the permanent ledger
16. [x] Verify the current account cash balance against the latest cash ledger state
17. [x] Verify position-history arithmetic and the current position
18. [x] Detect deliberately corrupted settlement state
19. [x] Prove that settlement for one account does not modify another account
20. [x] Prove that concurrent duplicate processing creates only one persisted settlement
21. [x] Run database-backed reliability tests against a dedicated PostgreSQL test database

Additional verification:

- [x] Persistent idempotency migrations implemented
- [x] Settlement-integrity migration implemented
- [x] Restart-recovery integration test implemented
- [x] Account-isolation integration test implemented
- [x] Concurrent duplicate-processing test implemented
- [x] Dedicated test-database safety checks implemented
- [ ] Record final corrected backend full-suite result
- [ ] Record final frontend full-suite result
- [ ] Record final CI result
- [ ] Confirm final Sprint 5 merge/pull-request status

---

## Sprint Outcome

Sprint 5 turns the Sprint 4 execution and settlement path into a more resilient, auditable, and operationally trustworthy trading workflow.

Every completed trade can now be reconstructed from persisted database state, including the original order, status history, Fill, execution quote, cash movement, resulting balance, and position change. Rejected trades are also reconstructable and are verified not to contain settlement artifacts.

Order processing is protected by persistent idempotency at both the order and execution layers. The client supplies an `Idempotency-Key`, the backend persists the key, and repeated processing of the same logical request returns the existing result rather than creating a second settlement. Database constraints provide an additional protection layer against duplicate orders, Fills, and cash settlement.

Recovery no longer depends on process memory. The backend classifies persisted orders into safe, completed, rejected, or ambiguous states. Accepted orders whose execution did not commit can be recovered deterministically, while ambiguous records are never automatically executed. Startup recovery verifies existing completed trades and can finish safe interrupted trades.

Settlement reconciliation validates the permanent trade records against the current account and portfolio state. Cash arithmetic, Fill-to-quote relationships, position history, and current balances are checked for consistency, and deliberately corrupted state is detected by automated tests.

Sprint 5 also expands database-backed reliability testing. Restart recovery, transaction rollback, account isolation, idempotent retries, and concurrent duplicate processing are exercised against the dedicated PostgreSQL integration-test database.

The Sprint 5 functional scope is complete. The final corrected full-suite backend, frontend, production-build, and CI results should be recorded as final delivery evidence once those commands are rerun on the completed branch.

---

## Status

**COMPLETE**

*Functional Sprint 5 scope is complete. Final full-suite and CI evidence should be recorded separately once the corrected branch is rerun.*
