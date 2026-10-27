# Sprint 7

## Sprint Goal

Improve the LEAP customer experience with personal watchlists, recurring price alerts, interactive market and portfolio charts, and a trading assistant that can submit explicitly confirmed stock orders and retrieve current holdings. Package the application so the complete stack can run through Docker Compose.

**Sprint Goal Status: COMPLETE — implementation reviewed**

This document records the implementation on `main` at commit [`17fc808`](https://github.com/williamli32/group-GOATteen/commit/17fc8080c9035085144d57c2f6c921584d4efd59), following the merge of `feature/chatbot` in [PR #68](https://github.com/williamli32/group-GOATteen/pull/68), reviewed on 27 October 2026. It covers the customer-facing work excluded from [Sprint 6](sprint-6.md), together with the dashboard, chatbot and deployment refinements now included in `main`.

Completion below describes implemented functionality and available automated coverage. Test execution and deployment evidence are described separately under Testing and Verification.

---

## User Stories

### 1. Personal Watchlists and Daily Price Changes

As a client, I want to save instruments and see their current prices and daily changes so that I can monitor the instruments relevant to me.

#### Acceptance Criteria

- [x] The dashboard separates Account overview, Watchlist, Orders and Holdings into tabs
- [x] An authenticated user can list, add and remove personal watchlist instruments
- [x] Watchlist entries persist in the database across sessions
- [x] Watchlist ownership is derived from the signed-in user
- [x] Adding an existing entry and removing an absent entry are idempotent
- [x] New watchlist entries require a valid, currently tradable instrument
- [x] Watchlist prices update from the dashboard's market refresh
- [x] An older market response cannot replace a newer watchlist quote or pair it with an unrelated daily percentage
- [x] A watched instrument can be selected for the existing order ticket when current market data is available
- [x] Daily percentage changes use the first recorded price of the current UTC day
- [x] Daily reference prices persist independently of normal quote cleanup
- [x] Positive, negative, zero and unavailable daily changes have distinct display states
- [x] Reference date, time and price are available in the daily-change description
- [x] Loading, empty, retry and failed-change states are handled

**Status: COMPLETE**

Daily change is `(latest price - reference price) / reference price × 100`. The reference is the simulator's first recorded price that UTC day; it is not an exchange's previous closing price. Missing reference data is shown as unavailable.

---

### 2. Persistent and Recurring Price Alerts

As a client, I want to configure target prices and receive in-app notifications when those conditions are met so that I can monitor price movements without keeping the Watchlist tab open.

#### Acceptance Criteria

- [x] A user can create, edit, pause, resume and delete their own price alerts
- [x] Alerts support ABOVE and BELOW conditions with positive target prices
- [x] ABOVE includes a quote equal to the threshold; BELOW also includes equality
- [x] Alert configuration and triggered notifications persist in PostgreSQL
- [x] The backend evaluates alerts on a configurable schedule, every three seconds by default
- [x] Evaluation excludes stale, future-dated and non-tradable instrument quotes
- [x] A qualifying quote triggers one notification for the current alert generation
- [x] The alert waits for a fresh quote on the non-qualifying side before automatically rearming
- [x] A subsequent qualifying crossing can trigger another notification
- [x] Row locking, transactional updates and notification uniqueness prevent duplicate delivery for one generation
- [x] Editing an alert or resuming a paused alert starts a fresh monitoring cycle
- [x] Notification evidence retains the symbol, threshold, observed price, currency and source quote
- [x] Notifications can be cleared individually or together without deleting the configured alerts or their stored price evidence
- [x] Dashboard notification popups operate across dashboard tabs
- [x] Popup IDs are remembered per account within the browser session to reduce repeated popups
- [x] Late refresh responses cannot restore notifications successfully cleared by the user
- [x] Alert and notification operations are scoped to the authenticated user

**Status: COMPLETE**

For an ABOVE alert at 100, a fresh price of 100 or more triggers a notification. Further qualifying quotes do not repeatedly notify. A fresh price below 100 rearms the alert, and the next price of 100 or more can notify again. The BELOW condition follows the inverse rule.

These alerts provide in-app notifications. They do not submit orders or send email, SMS or operating-system push notifications.

---

### 3. Interactive Charts, Live Portfolio Value and Current Holdings

As a client, I want to inspect changing prices, portfolio value and investment allocation so that I can understand the value and composition of my account.

#### Acceptance Criteria

- [x] The selected instrument has an interactive price chart with captured live quotes
- [x] Instrument charts distinguish illustrative history from captured quote observations
- [x] Stale data and failed refreshes show an updates-paused state
- [x] Chart inspection supports pointer interaction and keyboard navigation
- [x] The portfolio chart defaults to LIVE and records observed valuations during the current application visit
- [x] Live portfolio value includes available cash plus held quantities valued at current last prices
- [x] Holdings in other currencies are converted into the account currency using daily reference FX
- [x] The existing three-second dashboard refresh supplies live valuation observations
- [x] Live history is bounded to one hour and 1,200 observations per account/currency cache entry
- [x] Daily portfolio history remains separately available through 5D, 1M, YTD, 1Y and ALL controls
- [x] Daily history reconstructs cash and quantities from persisted account records and uses available historical prices and FX
- [x] Missing or stale valuation inputs produce unavailable observations and line gaps
- [x] Genuine zero portfolio values remain valid observations
- [x] The value axis adapts to the observed range so smaller changes remain visible
- [x] Hovering shows a themed tooltip beside the pointer with time, value and the available adjacent-point change
- [x] The tooltip flips or clamps its position to remain within the viewport
- [x] Pointer leave, scrolling, resize and Escape dismiss the floating tooltip
- [x] Persistent inspection details and data-table access provide alternatives to hovering
- [x] Holdings allocation shows estimated market values in the account currency
- [x] Positions with zero quantity are excluded from the Holdings table and displayed position counts
- [x] A partial sale leaves the remaining position visible; buying again restores a fully closed position to the display

**Status: COMPLETE**

The three chart sources have different meanings:

| View                            | Data and Interpretation                                                                                           |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Instrument rolling 24-hour view | A labelled illustrative segment followed by captured simulator quotes; the generated segment is display-only      |
| Instrument live detail          | Captured quote observations from the application visit                                                            |
| Portfolio LIVE                  | Actual observed account valuations from the visit; no illustrative portfolio values are generated                 |
| Portfolio daily ranges          | Reconstructed daily account valuations using persisted cash, position history and available historical pricing/FX |

Live portfolio history is held in a bounded application-memory cache; a full page reload starts a new visit. A cash-only account can correctly produce a flat line. Portfolio value is an estimate, and changes can reflect funding, withdrawals and trades as well as market movements; the chart is not a cash-flow-adjusted investment-return calculation.

Closed positions are hidden in the customer display. Their database records, orders, Fills and position-history evidence remain available for reconstruction and audit.

---

### 4. Floating Trading Assistant

As a client, I want a chat interface that can help me buy or sell a stock by ticker and retrieve my holdings so that I can perform common account tasks from the dashboard.

#### Acceptance Criteria

- [x] A round chat icon remains at the bottom right of the dashboard
- [x] Clicking the icon opens a themed, responsive chat panel
- [x] The launcher exposes an accessible name, expanded state and panel relationship
- [x] Opening focuses the composer; closing or pressing Escape returns focus to the launcher
- [x] Minimizing preserves conversation messages, an unsubmitted trade preview and unsent text
- [x] A confirmed request can finish while minimized without being resubmitted on reopening
- [x] BUY and SELL commands accept an exact tradable stock ticker and positive quantity
- [x] A missing quantity prompts a follow-up question
- [x] Unsupported, ambiguous, negated and compound commands do not submit trades
- [x] A trade preview requires explicit confirmation before an order is submitted
- [x] The preview uses the ask for BUY and the bid for SELL
- [x] An expired preview must be refreshed and confirmed again
- [x] The existing authenticated order API and backend execution controls remain authoritative
- [x] Confirmed order details and their idempotency key are saved before submission
- [x] An uncertain result is checked using the same immutable request and key
- [x] Restoring a pending request after reload does not automatically resubmit it
- [x] Only a server-reported FILLED result is described as completed
- [x] An unresolved submitted request blocks a new trade until its outcome is resolved
- [x] Holdings questions fetch the authenticated account's current holdings rather than relying on conversation history
- [x] Holdings replies show available cash and open positions, with optional ticker filtering
- [x] Account changes clear the previous account's conversation and unsubmitted draft

**Status: COMPLETE**

Example commands are `buy 5 AAPL`, `sell 2 AAPL`, `show my holdings` and `my AAPL holdings`. Tickers must exist in the platform's tradable equity universe. A ticker-only trade instruction such as `buy AAPL` asks for the quantity before preparing the preview.

The assistant uses deterministic command parsing. It requires no LLM service, model API key or additional container. Cancelling an unsubmitted draft discards it; chat does not provide cancellation of an already submitted order. Conversation history is not a permanent chat archive.

---

### 5. Complete Application Container Deployment

As a developer or deployment operator, I want to build and run the complete application through one Compose configuration so that the application does not depend on separately running local frontend and Java development processes.

#### Acceptance Criteria

- [x] `compose.deploy.yaml` defines the complete application stack
- [x] The frontend builds the Angular production bundle and serves it through Caddy
- [x] Caddy forwards `/api` requests to the trading service and supports SPA routes
- [x] Trading, market data and PostgreSQL communicate through Docker service names
- [x] The browser reaches the application through the frontend entry point
- [x] A generated `.env.deploy` supplies deployment credentials without committing them to source control
- [x] The environment generator refuses to overwrite an existing credentials file
- [x] Named volumes persist the deployment database and Caddy state across rebuilds
- [x] Health checks and dependency conditions control service startup readiness
- [x] A read-only smoke-check script verifies frontend routes, API routing and authentication boundaries
- [x] Deployment instructions cover local access, a remote-host SSH tunnel and optional hostname-based HTTPS
- [x] The original database-only development Compose setup remains separately available

**Status: COMPLETE**

| Container             | Responsibility                                                                   |
| --------------------- | -------------------------------------------------------------------------------- |
| `frontend`            | Angular production bundle, Caddy, API proxy and optional HTTPS                   |
| `trading-platform`    | Authenticated trading/account APIs, settlement, alerts, reporting and migrations |
| `market-data-service` | Simulated market quote generation                                                |
| `postgres`            | Persistent application database and dedicated application login                  |

The configuration supports deployment; it does not itself establish that an AWS release, public hostname or production smoke check has completed. The existing Jenkins pipeline tests the application and does not automatically publish or deploy these images.

---

## Supporting Account and Settlement Improvements

- [x] Cross-currency orders obtain daily reference FX for conversion into the account's currency
- [x] Each Fill retains the FX source/target currency, rate, rate date, provider and account-currency settlement amount
- [x] The backend obtains the FX decision at execution rather than trusting a frontend estimate
- [x] Unavailable, invalid, stale or future-dated FX is rejected rather than replaced by a guessed rate
- [x] Same-currency settlement uses rate 1; cross-currency BUY debits round up and SELL credits round down to the account currency's minor unit
- [x] The order ticket exposes an indicative conversion estimate
- [x] Modeled cash withdrawal validates a positive amount and available account cash
- [x] Withdrawal updates the authenticated account and cash ledger transactionally under an account lock
- [x] Account-scoped withdrawal request keys make retries idempotent and reject conflicting reuse

Supported signup/account UI currencies remain USD, GBP and INR. FX uses Frankfurter daily reference rates for this simulated platform. Funding and withdrawal are modeled account operations, without an external banking or payment integration. Instrument prices are generated by the market simulator, rather than obtained from a live exchange.

---

## Technical Deliverables

### API Surface

All endpoints below require authentication. User and account ownership are resolved server-side.

| Method / Endpoint                                 | Purpose                                                              |
| ------------------------------------------------- | -------------------------------------------------------------------- |
| `GET /api/watchlist`                              | Read the user's saved instruments                                    |
| `PUT`, `DELETE /api/watchlist/{instrumentId}`     | Add or remove a watchlist entry                                      |
| `GET`, `POST /api/price-alerts`                   | List or create price alerts                                          |
| `PUT`, `DELETE /api/price-alerts/{id}`            | Edit or delete an owned alert                                        |
| `PATCH /api/price-alerts/{id}/enabled`            | Pause or resume an alert                                             |
| `GET`, `DELETE /api/price-alerts/notifications`   | List visible notifications or clear the inbox                        |
| `DELETE /api/price-alerts/notifications/{id}`     | Clear an individual notification                                     |
| `PATCH /api/price-alerts/notifications/{id}/read` | Retained notification-read compatibility endpoint                    |
| `GET /api/market/instruments`                     | Current tradable market data and available daily-change information  |
| `GET /api/accounts/me/portfolio-history?days=5`   | Reconstructed daily portfolio history                                |
| `GET /api/fx/rate?from=USD&to=GBP`                | Indicative reference FX conversion                                   |
| `POST /api/accounts/me/funds/withdraw`            | Idempotent modeled cash withdrawal                                   |
| `POST /api/orders`                                | Existing order submission reused by chat; requires `Idempotency-Key` |
| `GET /api/accounts/me/holdings`                   | Existing current holdings endpoint reused by chat                    |

The portfolio-history API accepts 1–365 days, `-1` for YTD and `0` for all account history. LIVE observations are maintained in the frontend; they do not introduce a new streaming or historical API.

### Database Migrations

| Migration                                  | Purpose                                                                       |
| ------------------------------------------ | ----------------------------------------------------------------------------- |
| `V22__create_personal_watchlists.sql`      | User-owned watchlists and duplicate-entry prevention                          |
| `V23__create_daily_market_baselines.sql`   | Persistent first-observed UTC daily reference prices                          |
| `V24__record_fill_fx_settlement.sql`       | Persist FX decisions and converted Fill settlement amounts                    |
| `V25__persistent_price_alerts.sql`         | Alert configuration, notification evidence and generation uniqueness          |
| `V26__auto_rearm_price_alerts.sql`         | Recurring alert armed/waiting state and migration of earlier triggered alerts |
| `V27__clear_price_alert_notifications.sql` | Hide cleared notifications while retaining evidence                           |
| `V28__add_withdrawal_idempotency_keys.sql` | Account-scoped withdrawal uniqueness and debit constraints                    |
| `V29__index_portfolio_history_reads.sql`   | Index account cash and position-history lookups                               |

The floating chat launcher, live portfolio observation cache, hover tooltip and zero-quantity display filter add no further database migrations.

---

## Testing and Verification

### Automated Coverage Present in the Repository

| Area                                                                                 | Representative Tests                                                                                                                          |
| ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Watchlist persistence, duplicate requests, ownership and authentication              | `WatchlistIntegrationTest`, `WatchlistControllerSecurityTest`, `watchlist.component.spec.ts`                                                  |
| Daily-change calculations and retained daily baselines                               | `DailyPriceChangeTest`, `DailyMarketBaselineIntegrationTest`, `daily-price-change.component.spec.ts`                                          |
| Alert validation, recurring transitions, persistence and notification ownership      | `PriceAlertServiceTest`, `PriceAlertEvaluatorTest`, `PriceAlertIntegrationTest`                                                               |
| Alert editing, clearing, refresh races and dashboard popups                          | `price-alerts.component.spec.ts`, `price-alert-popup-listener.component.spec.ts`                                                              |
| FX and reconstructed daily valuations                                                | `FxRateServiceTest`, `HistoricalFxRatesTest`, `PortfolioHistoryIntegrationTest`                                                               |
| Withdrawal validation, replay and ownership                                          | `AccountFundingServiceTest`, `AccountFundingIntegrationTest`, `AccountFundingControllerSecurityTest`                                          |
| Instrument observations, illustrative data and chart interaction                     | `instrument-price-history.spec.ts`, `instrument-price-chart.component.spec.ts`                                                                |
| Portfolio observations, gaps, range selection, tooltip and open positions            | `portfolio-live-history.spec.ts`, `portfolio-chart.component.spec.ts`, `holdings-allocation.component.spec.ts`, `dashboard.component.spec.ts` |
| Chat command parsing, confirmation, retries, account isolation and floating panel    | `chatbot-command.spec.ts`, `trading-chatbot.component.spec.ts`                                                                                |
| Browser chat interactions                                                            | `trading-chatbot.spec.ts`                                                                                                                     |
| Browser live valuation, feed failures, tooltip themes and position closure/reopening | `portfolio-live.spec.ts`, `portfolio-interactions.spec.ts`                                                                                    |

The chatbot and portfolio Playwright configurations each run three scenarios across desktop, tablet and phone viewports. These suites use controlled API fixtures, so their success verifies browser behavior rather than real backend settlement or a deployed environment.

This documentation update does not claim a new full frontend, Maven, Jenkins or deployment test run. The checks performed for the document are Markdown formatting, local reference validation and a whitespace check. Runtime results should be attached to the tested commit when preparing release evidence.

### Reproducible Checks

From `frontend/`:

```bash
npm ci
npm test -- --watch=false
npm run build
npm run test:e2e:types
npx playwright install chromium
npm run test:e2e:chatbot
npm run test:e2e:portfolio
```

For backend verification, configure the dedicated test database and `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` and `JWT_SECRET` as described in [Sprint 6](sprint-6.md). From `backend/trading-platform/`, run `./mvnw test` on Linux or `.\mvnw.cmd test` in PowerShell with Java 21. Database-backed fixture cleanup requires a dedicated database whose name ends in `_test`.

### Updating an Existing Deployment from Main

From the repository root after the changes have been pushed to `main`:

```bash
git switch main
git pull --ff-only origin main
docker-compose --env-file .env.deploy -f compose.deploy.yaml config --quiet
docker-compose --env-file .env.deploy -f compose.deploy.yaml up -d --build --wait --wait-timeout 240
docker-compose --env-file .env.deploy -f compose.deploy.yaml ps
python deploy/smoke-check.py http://localhost:8082
```

Reuse the existing `.env.deploy`. Run `python deploy/init-env.py` only for initial setup when that file does not exist. Use `python3` or `docker compose` if those are the command names installed on the host. Rebuilding preserves named volumes; `down -v` removes them and is not an upgrade step.

The URL assumes the default local port. When Docker runs on a remote Linux host, run the smoke check there or use the documented frontend SSH tunnel. For hostname-based HTTPS, check the configured application origin instead. See [Container deployment](container-deployment.md) for full setup and migration instructions.

---

## Definition of Done

1. [x] Personal watchlists and daily changes are available in the tabbed customer dashboard
2. [x] Persistent price alerts notify on qualifying prices and rearm after a reset crossing
3. [x] Portfolio value updates with observed account and market changes, with explicit gaps for unavailable inputs
4. [x] Historical portfolio views, investment allocation and chart inspection remain available
5. [x] Fully sold positions disappear from the active Holdings display while persisted evidence is retained
6. [x] The floating assistant retrieves current holdings and requires confirmation before submitting stock orders
7. [x] Chat retries preserve the original order payload and idempotency key
8. [x] Cross-currency settlement evidence and modeled withdrawals are persisted with their required controls
9. [x] The full application has a Compose deployment configuration and read-only smoke-check script
10. [x] Automated coverage and repeatable verification commands are included

---

## Sprint Outcome

Sprint 7 gives clients a more useful dashboard for monitoring instruments, receiving recurring price alerts, inspecting portfolio value and managing current positions. The floating assistant makes common stock-order and holdings tasks available throughout the dashboard while retaining explicit confirmation and the existing backend settlement controls.

The complete application can be built and started through the four-container deployment configuration. Source delivery is recorded against the reviewed `main` commit; environment-specific test runs and release acceptance remain separately verifiable evidence.

---

## Implementation References

- [Sprint 6 — reporting and internal interfaces](sprint-6.md)
- [Customer dashboard and chart components](../frontend/src/app/dashboard)
- [Watchlist backend](../backend/trading-platform/src/main/java/com/goatteen/trading/watchlist)
- [Price-alert backend](../backend/trading-platform/src/main/java/com/goatteen/trading/alerts)
- [Daily market baselines](../backend/trading-platform/src/main/java/com/goatteen/trading/marketdata/DailyMarketBaselineRepository.java)
- [Portfolio-history reconstruction](../backend/trading-platform/src/main/java/com/goatteen/trading/account/PortfolioHistoryService.java)
- [FX conversion and historical rates](../backend/trading-platform/src/main/java/com/goatteen/trading/fx)
- [Trading assistant](../frontend/src/app/dashboard/trading-chatbot.component.ts)
- [Chat command parser](../frontend/src/app/dashboard/chatbot-command.ts)
- [Browser tests](../frontend/e2e)
- [Database migrations](../backend/trading-platform/src/main/resources/db/migration)
- [Container deployment guide](container-deployment.md)
- [Full-stack Compose configuration](../compose.deploy.yaml)
- [Deployment smoke check](../deploy/smoke-check.py)

---

## Status

**COMPLETE — implementation reviewed on `main` at `17fc808`.**
