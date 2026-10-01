# LEAP Trading Platform Backend

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Java](https://img.shields.io/badge/java-21-blue)]()
[![Spring Boot](https://img.shields.io/badge/spring%20boot-3.5.5-green)]()
[![PostgreSQL](https://img.shields.io/badge/postgresql-17-336791)]()

A high-performance REST API for order execution, portfolio management, and market data integration built with Spring Boot 3.5.5 and Java 21.

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Setup & Installation](#setup--installation)
- [Configuration](#configuration)
- [Database](#database)
- [API Documentation](#api-documentation)
- [Development](#development)
- [Testing](#testing)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)
- [Key Features Deep Dive](#key-features-deep-dive)

---

## Features

### Core Trading Features
- ✅ **Order Management** - Submit, accept, execute, and track orders
- ✅ **Portfolio Tracking** - Real-time position and cash balance management
- ✅ **Market Data Integration** - Stream live quotes from market data service
- ✅ **Instrument Universe** - Support for stocks, ETFs, forex, and crypto
- ✅ **Audit Trail** - Complete transaction history with timestamps

### Advanced Capabilities
- ✅ **Idempotent Order Execution** - Duplicate protection with unique keys
- ✅ **Pessimistic Locking** - Prevent concurrent execution of same order
- ✅ **Optimistic Locking** - Safe concurrent updates with version control
- ✅ **Scheduled Sync** - Automatic market data and position synchronization
- ✅ **JWT Authentication** - Secure REST API with token-based auth

### Enterprise Features
- ✅ **Flyway Migrations** - Version-controlled database schema
- ✅ **Comprehensive Logging** - SQL and application-level logging
- ✅ **REST Validation** - Request validation with detailed error responses
- ✅ **OpenAPI/Swagger** - Interactive API documentation at `/swagger-ui.html`

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **JVM** | Java | 21 (LTS) |
| **Framework** | Spring Boot | 3.5.5 |
| **Web** | Spring MVC | 6.x |
| **Persistence** | Spring Data JPA | 3.x |
| **ORM** | Hibernate | 6.6.26 |
| **Database** | PostgreSQL | 17 |
| **Migrations** | Flyway | 10.x |
| **Authentication** | JWT (jjwt) | 0.12.6 |
| **Testing** | JUnit 5 + Mockito | 5.x |
| **Documentation** | SpringDoc OpenAPI | 2.8.13 |
| **Build** | Maven | 3.9.9 |

---

## Project Structure

The project follows a **domain-driven design** with modules organized by business capability:

| Directory | Purpose |
|-----------|---------|
| `execution/` | Order execution with idempotency protection (pessimistic locking + duplicate key detection) |
| `order/` | Order state management (SUBMITTED → ACCEPTED → FILLED/REJECTED) |
| `auth/` | JWT token generation, validation, and session management |
| `portfolio/` | Position tracking, cash management, P&L calculation |
| `marketdata/` | Integration with market data service, quote synchronization |
| `audit/` | Audit trail for all trading operations |
| `account/` | Account and client management |
| `instrument/` | Instrument metadata (stocks, ETFs, forex, crypto) |
| `common/` | Shared utilities, enums, exceptions |
| `config/` | Spring configuration and security config |
| `db/migration/` | 14 Flyway migrations (versioned database schema) |

**Build & Configuration**:
- `pom.xml` - Maven dependencies and plugins
- `mvnw/mvnw.cmd` - Maven wrapper (no installation needed)
- `Dockerfile` - Container image definition
- `src/main/resources/application*.yaml` - Configuration by profile (dev, test, prod)

---

## Prerequisites

### System Requirements
- **Java 21+** (LTS - Long Term Support)
- **Maven 3.9.x** or higher
- **PostgreSQL 17+**
- **Git**

### Verify Installation
```bash
java -version              # Should show Java 21+
mvn --version              # Should show Maven 3.9+
psql --version             # Should show PostgreSQL 17+
```

---

## Setup & Installation

### 1. Clone the Repository
```bash
git clone https://github.com/your-org/group-GOATteen.git
cd group-GOATteen/backend/trading-platform
```

### 2. Database Setup

#### Remote Database (via SSH Tunnel)

To establish connection to Docker from Neueda Windows VM

```bash
# Establish SSH tunnel to remote PostgreSQL
ssh -L 5433:localhost:5432 ec2-user@10.14.141.220

# In another terminal, verify connection:
docker-compose up -d
docker exec -it leap-postgres psql -U postgres -d leap_trading
```

### 3. Build the Project
```bash
cd backend/trading-platform

# Clean build, with tests
mvn clean package  # Runs all tests (requires database)

# Without tests
mvn clean package -DskipTests
```

### 4. Run the Application

#### From Command Line
```bash
mvn spring-boot:run

# Equivalent command
./mvnw spring-boot:run
```

---

## Configuration

### Application Profiles

The application uses Spring profiles for 
environment-specific configuration. There are application and application-dev YAML files with configuration for the database.

### Key Configuration Properties

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate              # Don't modify schema at startup
    show-sql: true                    # Log SQL queries
  
  flyway:
    enabled: true                     # Enable automatic migrations
    locations: classpath:db/migration
    validateOnMigrate: false          # Skip validation on startup

app:
  jwt:
    accessTokenExpirationMinutes: 60  # Token valid for 1 hour
  
  auth:
    refreshTokenExpirationDays: 7     # Refresh token valid for 7 days
```

> **Security Note**: Sensitive configuration (database credentials, JWT secrets, API keys) should be provided at runtime via your deployment platform's secret management system (Kubernetes Secrets, AWS Secrets Manager, HashiCorp Vault, etc.). Never commit secrets to version control.

---

## Database

### Schema Overview

The database consists of **6 main schemas** managed by **14 Flyway migrations** (for now):

#### 1. Identity Schema (V1)
```sql
-- Users and authentication
users (user_id, username, email, created_at)
roles (role_id, role_name)
user_roles (user_id, role_id)
```

#### 2. Trading Schema (V2)
```sql
-- Core trading data
clients (client_id, name)
accounts (account_id, client_id, cash_balance, account_status)
orders (order_id, account_id, symbol, side, quantity, status)
fills (fill_id, order_id, fill_price, fill_quantity, idempotency_key)
positions (position_id, account_id, symbol, quantity, average_cost)
```

#### 3. Market Data Schema (V3+)
```sql
-- Historical quotes and instruments
instruments (instrument_id, symbol, name, type)
market_quote_history (quote_id, symbol, price, timestamp)
tradable_universe (instrument_id, is_tradable)
```

### Schema Versions

| Version | Description | Status |
|---------|-------------|--------|
| V1 | Create identity schema (users, roles) | ✅ Applied |
| V2 | Create trading schema (orders, fills, positions) | ✅ Applied |
| V3 | Fix market quote history | ✅ Applied |
| V4 | Add optimistic locking (version fields) | ✅ Applied |
| V5 | Create auth sessions | ✅ Applied |
| V6 | Seed market data (instruments, quotes) | ✅ Applied |
| V7-10 | Expand instrument universe (stocks, ETFs, forex, crypto) | ✅ Applied |
| V11-13 | Performance & compliance improvements | ✅ Applied |
| V14 | Add idempotency_key to fills (unique constraint) | ✅ Applied |

### Troubleshooting Database Issues

#### Flyway Migration Failed
```bash
# Repair Flyway metadata (use with caution!)
mvn flyway:repair

# Re-run migrations
mvn flyway:migrate
```

#### Connection Refused
```bash
# Check if SSH tunnel is active
ps aux | grep ssh

# Verify PostgreSQL is running
pg_isready -h localhost -p 5433
```

## API Documentation

### Interactive API Docs
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

### Core Endpoints

#### Authentication
```
POST   /api/auth/register              # Register new user
POST   /api/auth/login                 # Login (returns JWT token)
POST   /api/auth/refresh               # Refresh access token
POST   /api/auth/logout                # Logout
```

#### Order Management
```
GET    /api/orders                     # List all orders for account
POST   /api/orders                     # Submit new order
GET    /api/orders/{orderId}           # Get order details
POST   /api/orders/{orderId}/accept    # Accept order (SUBMITTED → ACCEPTED)
POST   /api/orders/{orderId}/reject    # Reject order (SUBMITTED → REJECTED)
POST   /api/orders/execute/{orderId}   # Execute order (ACCEPTED → FILLED)
```

#### Portfolio
```
GET    /api/accounts                   # List all accounts
GET    /api/accounts/{accountId}       # Get account details
GET    /api/accounts/{accountId}/positions  # Get positions
GET    /api/accounts/{accountId}/blotter    # Get order history
```

#### Market Data
```
GET    /api/instruments                # List all instruments
GET    /api/market/quotes?symbol=AAPL  # Get latest quotes
GET    /api/market/history?symbol=AAPL # Get quote history
```

### Order State Machine

```
┌──────────┐
│SUBMITTED │  (new order)
└─────┬────┘
      │
      ├─→ POST /accept  ─→ ┌──────────┐
      │                     │ ACCEPTED │  (ready to fill)
      │                     └─────┬────┘
      │                           │
      │                           └─→ POST /execute  ─→ ┌────────┐
      │                                                  │ FILLED │  (terminal)
      │                                                  └────────┘
      │
      └─→ POST /reject  ─→ ┌──────────┐
                           │ REJECTED │  (terminal)
                           └──────────┘
```

### Error Responses

```json
{
  "timestamp": "2026-10-01T16:25:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Order quantity must be positive",
  "path": "/api/orders"
}
```

| Status | Meaning |
|--------|---------|
| 200 | Success |
| 201 | Created |
| 204 | No Content |
| 400 | Bad Request (validation error) |
| 401 | Unauthorized (missing/invalid token) |
| 403 | Forbidden (insufficient permissions) |
| 409 | Conflict (wrong order state) |
| 500 | Internal Server Error |

---

## Development

### Code Conventions

- **Naming**: PascalCase for classes, camelCase for variables/methods
- **Packages**: Group by domain (auth, order, portfolio, etc.)
- **Logging**: Use SLF4J via `@Slf4j` annotation
- **Comments**: Document public methods, complex logic, and business rules
- **Error Handling**: Create custom exceptions extending `RuntimeException`

## Testing

### Test Structure

STUB - update later

### Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Detailed metrics
curl http://localhost:8080/actuator/metrics

# Database connectivity
curl http://localhost:8080/actuator/health/db
```

## Troubleshooting

### Application Won't Start

#### Error: "Connection refused" to PostgreSQL
```
Solution:
1. Verify PostgreSQL is running: pg_isready -h localhost -p 5433
2. Check SSH tunnel: ps aux | grep ssh
3. Verify datasource configuration is correct
4. Test connection: psql -h localhost -p 5433 -U postgres -d leap_trading
```

#### Error: "Flyway validation failed"
```
Solution:
1. Check if migrations are out of sync: mvn flyway:info
2. Repair metadata: mvn flyway:repair
3. Re-run migrations: mvn flyway:migrate
4. Clear compiled bytecode: mvn clean
```

#### Error: "Port 8080 already in use"
```bash
# Find process using port 8080
lsof -i :8080

# Kill process
kill -9 <PID>

# Or use different port:
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

### Order State Machine

**Valid Transitions**:
- SUBMITTED → ACCEPTED → FILLED (terminal)
- SUBMITTED → REJECTED (terminal)
- ACCEPTED → REJECTED (terminal)

**Enforced In**:
```java
// Order.java
public void validateTransitionToAccepted() {
    if (status != OrderStatus.SUBMITTED) {
        throw new IllegalStateException("Only SUBMITTED orders can be accepted");
    }
}
```

---

## Contributing

### Pull Request Workflow
1. Create feature branch: `git checkout -b feature/description`
2. Make changes and commit: `git commit -m "feat: description"`
3. Push to remote: `git push origin feature/description`
4. Create Pull Request on GitHub
5. Ensure CI passes (tests, build)
6. Get code review approval
7. Merge to main

### Commit Message Convention
```
feat:    Add new feature
fix:     Fix a bug
docs:    Update documentation
test:    Add or update tests
refactor: Refactor code without changing behavior
chore:   Update dependencies, configs
```

### Code Review Checklist
- [ ] Tests pass (`mvn clean test`)
- [ ] Build succeeds (`mvn clean package`)
- [ ] No SQL/N+1 queries issues
- [ ] Follows code conventions
- [ ] Documentation updated
- [ ] No secrets committed to repository
