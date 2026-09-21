# Sprint 3

## Sprint Goal

Implement market data integration, trading UI functionality, and order submission/validation capabilities for the LEAP trading platform.

**Sprint Goal Status: COMPLETE**

---

## User Stories

### 1. Market Instrument Browsing

As a client, I want to browse available financial instruments so that I can view assets available for trading.

#### Acceptance Criteria

- [x] Client can view available tradable instruments from the dashboard
- [x] Instruments are retrieved through the Trading Platform API
- [x] Trading Platform consumes market data from the simulated market data service
- [x] Broader simulated market-data universe is supported by the market-data service
- [x] Client-facing tradable universe is curated to 50 instruments
- [x] Tradable instruments include equities, foreign exchange, and cryptocurrency assets
- [x] Equity coverage includes US, UK, and India markets
- [x] Duplicate symbols across different exchanges are supported using symbol and exchange identity
- [x] Dashboard displays instrument symbol, market, bid, ask, last price, and currency
- [x] Selected instruments display full instrument, market, and currency names in the order ticket

**Status: COMPLETE**

---

### 2. Simulated Market Data Feed

As a client, I want to see current market prices so that I can make informed trading decisions.

#### Acceptance Criteria

- [x] Market data microservice loads simulated market data from CSV
- [x] Simulated prices update automatically
- [x] Simulated market prices evolve continuously using scheduled price updates
- [x] Trading Platform synchronizes market prices from the simulator
- [x] Synchronization is limited to the curated tradable universe
- [x] Latest quotes are persisted in the trading database
- [x] Bid, ask, and last prices are generated for tradable instruments
- [x] Every tradable instrument has an available quote
- [x] Quote history retention prevents uncontrolled database growth
- [x] Quotes referenced by executed trades are protected from retention cleanup
- [x] Dashboard automatically refreshes market information
- [x] Frontend does not directly call the market data simulator

**Status: COMPLETE**

---

### 3. Trading Dashboard Market Integration

As a client, I want to see market information inside the authenticated dashboard so that trading data is available in one place.

#### Acceptance Criteria

- [x] Dashboard integrates with the Trading Platform market API
- [x] Market data loads after authentication
- [x] Selected instruments remain selected during refreshes when available
- [x] Background market refresh does not hide existing prices on temporary failures
- [x] Dashboard handles loading and error states
- [x] Responsive dashboard behavior is maintained
- [x] Market table displays concise exchange and currency identifiers
- [x] Order ticket displays full instrument, market, and currency names

**Status: COMPLETE**

---

### 4. Order Submission and Validation

As a client, I want to submit BUY and SELL orders so that I can request trades against available market instruments.

#### Acceptance Criteria

- [x] Authenticated clients can access order functionality
- [x] Client can submit BUY orders
- [x] Client can submit SELL orders
- [x] Orders are associated with the authenticated client's account
- [x] Orders are persisted before validation outcome is finalized
- [x] Orders are validated before acceptance
- [x] Orders are validated against current market quotes
- [x] BUY orders are valued using the current ask price
- [x] SELL orders validate available holdings
- [x] Non-tradable instruments are rejected
- [x] Invalid quantities are rejected
- [x] Orders without an available quote are rejected
- [x] Unsupported cross-currency BUY orders are rejected
- [x] Invalid orders receive a stored REJECTED status and rejection reason
- [x] Valid orders receive an ACCEPTED status
- [x] Order APIs remain protected by authentication
- [x] Clients can only retrieve orders belonging to their own account
- [x] Accepted Sprint 3 orders are not executed or settled yet

**Status: COMPLETE**

---

## Business Requirement Market Coverage

Sprint 3 satisfies the required instrument coverage for the LEAP market-data and trading interface.

The client-facing tradable universe contains:

| Instrument Category | Count |
|---|---:|
| US Equities | 20 |
| India Equities | 15 |
| UK Equities | 10 |
| Foreign Exchange | 3 |
| Cryptocurrency | 2 |
| **Total** | **50** |

### Foreign Exchange Instruments

- GBPUSD - British Pound / US Dollar
- EURUSD - Euro / US Dollar
- USDJPY - US Dollar / Japanese Yen

### Cryptocurrency Instruments

- BTCUSD - Bitcoin / US Dollar
- ETHUSD - Ethereum / US Dollar

This provides the required coverage of:

- US equities
- UK equities
- India equities
- Foreign exchange
- Cryptocurrency

The larger simulated dataset remains available to the market-data service, while the Trading Platform intentionally exposes a curated set of 50 mainstream tradable instruments to clients.

---

## Technical Deliverables

- [x] Simulated market data microservice
- [x] Market data CSV loading infrastructure
- [x] Scheduled simulated price movement
- [x] Market data REST API
- [x] Trading Platform market data client integration
- [x] Quote synchronization service
- [x] Market quote persistence
- [x] Bid/ask spread generation
- [x] Expanded instrument database migration
- [x] Symbol-and-exchange instrument identity
- [x] Quote retention and indexing support
- [x] Broader simulator instrument universe support
- [x] Curated 50-instrument tradable universe
- [x] US, UK, and India equity coverage
- [x] FX instrument coverage
- [x] Cryptocurrency instrument coverage
- [x] Trading Platform market API
- [x] Angular market data service integration
- [x] Dashboard market table integration
- [x] Automatic quote refresh handling
- [x] Full instrument, exchange, and currency labels in the order ticket
- [x] Order ticket integration
- [x] Order validation against market prices
- [x] Authenticated account-derived order ownership
- [x] Protected order retrieval
- [x] Backend order validation tests
- [x] Frontend market dashboard tests
- [x] Maven backend verification
- [x] Angular production build verification
- [x] Jenkins CI verification

---

## Testing and CI Results

### Frontend

Angular/Vitest verification completed successfully:

- Dashboard tests passed
- Market data service tests passed
- Order service tests passed
- Authentication tests remained passing
- Production Angular build completed successfully

### Backend

Spring Boot/Maven verification completed successfully:

- Backend tests passed
- Order validation tests passed
- Order controller tests passed
- Market data synchronization completed successfully
- Quote persistence verified
- Instrument universe migrations verified
- Curated tradable market migration verified

### Market Data Verification

The final Sprint 3 market configuration was verified with:

- **50 client-facing tradable instruments**
- **45 equities**
- **3 foreign exchange instruments**
- **2 cryptocurrency instruments**
- **20 US equities**
- **15 India equities**
- **10 UK equities**
- **0 tradable instruments without quotes**
- Current quotes persisted for all tradable instruments
- Dashboard displays synchronized market data through the Trading Platform API
- Trading Platform synchronizes only the curated tradable universe
- Broader simulated market data remains available within the market-data service

### Jenkins

The complete CI pipeline completed successfully:

1. Source checkout
2. Frontend dependency installation
3. Frontend unit tests
4. Angular production build
5. Backend build and tests
6. PostgreSQL CI database verification
7. Cleanup

---

## Definition of Done

Sprint 3 is complete when a client can:

1. [x] View available market instruments
2. [x] View continuously changing simulated market prices
3. [x] Access US, UK, and India equities
4. [x] Access foreign exchange instruments
5. [x] Access cryptocurrency instruments
6. [x] Browse market information through the authenticated dashboard
7. [x] Select an instrument and view its full instrument, market, and currency information
8. [x] Submit BUY and SELL orders
9. [x] Receive order acceptance or rejection responses
10. [x] Have orders validated against current market data
11. [x] Prevent unsupported or invalid trading requests
12. [x] Use a responsive trading interface
13. [x] Access functionality verified through automated tests and CI

Additional completion requirements:

- [x] Backend tests pass
- [x] Frontend tests pass
- [x] Production builds succeed
- [x] Jenkins CI pipeline completes successfully
- [x] Sprint 3 functionality is committed to `feature/sprint3-trading`

---

## Sprint Outcome

Sprint 3 successfully introduced the market data and order-entry foundation of the LEAP trading platform.

Clients can now browse a curated universe of 50 tradable instruments covering US, UK, and India equities, foreign exchange, and cryptocurrency markets.

The market-data microservice maintains a broader simulated instrument dataset and continuously generates changing prices. The Trading Platform consumes this feed, synchronizes the curated tradable universe, persists bid, ask, and last-price quotes, and exposes the resulting market information through authenticated APIs.

The Angular dashboard displays synchronized market data and allows clients to select instruments and submit BUY or SELL orders. Submitted orders are associated with the authenticated client's account and validated against instrument tradability, quantity, current quotes, available cash, account currency, and existing holdings.

Valid Sprint 3 orders transition to `ACCEPTED`, while invalid orders transition to `REJECTED` with an appropriate rejection reason. Accepted orders are intentionally not executed or settled during Sprint 3.

Cross-currency conversion is not implemented in Sprint 3. A BUY order for an instrument whose currency differs from the client's account currency is therefore rejected rather than performing an implicit currency conversion.

The platform now provides the foundation required for Sprint 4, including:

- Accepted-order execution
- Execution against current market quotes
- Fill creation
- Atomic cash and position settlement
- Portfolio updates
- Cash transaction recording
- Order lifecycle completion
- Real-time trade and portfolio status updates

---

## Status

**COMPLETE**