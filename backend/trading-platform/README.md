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

### Optional
- **Docker** & **Docker Compose** (for containerized deployment)
- **IDE**: IntelliJ IDEA or VS Code with Java extensions
- **Postman** or **curl** for API testing

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

#### Option A: Remote Database (via SSH Tunnel)
```bash
# Establish SSH tunnel to remote PostgreSQL
ssh -L 5433:localhost:5432 ec2-user@10.14.141.220

# In another terminal, verify connection:
psql -h localhost -p 5433 -U postgres -d leap_trading
```

#### Option B: Local Docker PostgreSQL
```bash
cd ../..  # Go to project root
docker-compose up -d postgresql

# Verify:
docker ps  # Should show leap-postgres container
```

### 3. Build the Project
```bash
cd backend/trading-platform

# Clean build
mvn clean package

# With tests
mvn clean package  # Runs all tests (requires database)

# Without tests
mvn clean package -DskipTests
```

### 4. Run the Application

#### From Command Line
```bash
mvn spring-boot:run
```

#### From IDE
- Right-click `TradingPlatformApplication.java`
- Select "Run" or "Debug"

#### From JAR
```bash
java -jar target/trading-platform-0.0.1-SNAPSHOT.jar
```

### 5. Verify Application is Running
```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Access Swagger UI
open http://localhost:8080/swagger-ui.html
```

---

## Configuration

### Application Profiles

The application uses Spring profiles for environment-specific configuration:

```yaml
# src/main/resources/application.yaml (active profile)
spring:
  profiles:
    active: dev  # Can be: dev, test, prod

  datasource:
    url: jdbc:postgresql://localhost:5433/leap_trading
    username: postgres
```

### Environment-Specific Configs

| Profile | File | Use Case | Database |
|---------|------|----------|----------|
| **dev** | `application.yaml` | Local development | Remote (5433) |
| **test** | `application-test.yaml` | Unit tests | In-memory/test DB |
| **prod** | `application-prod.yaml` | Production | Remote secure DB |

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

The database consists of **6 main schemas** managed by **14 Flyway migrations**:

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

### Key Tables

#### `orders`
```sql
CREATE TABLE orders (
    order_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    side VARCHAR(10),                  -- 'BUY' or 'SELL'
    quantity BIGINT,
    status VARCHAR(50),                -- SUBMITTED, ACCEPTED, FILLED, REJECTED
    created_at TIMESTAMP,
    version BIGINT,                    -- Optimistic locking
    CONSTRAINT fk_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    CONSTRAINT fk_instrument FOREIGN KEY (instrument_id) REFERENCES instruments(instrument_id)
);
```

#### `fills`
```sql
CREATE TABLE fills (
    fill_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    fill_price DECIMAL(15, 6),
    fill_quantity BIGINT,
    idempotency_key VARCHAR(255) UNIQUE,  -- 🔑 Duplicate prevention
    executed_at TIMESTAMP,
    CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
);
CREATE INDEX idx_fills_idempotency ON fills(idempotency_key);
```

#### `positions`
```sql
CREATE TABLE positions (
    position_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    quantity BIGINT,
    average_cost DECIMAL(15, 6),
    version BIGINT,                    -- Optimistic locking
    CONSTRAINT fk_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    CONSTRAINT fk_instrument FOREIGN KEY (instrument_id) REFERENCES instruments(instrument_id)
);
```

### Accessing the Database

```bash
# Connect remotely via SSH tunnel
psql -h localhost -p 5433 -U postgres -d leap_trading

# Common queries
SELECT COUNT(*) FROM orders;
SELECT COUNT(*) FROM fills;
SELECT account_id, cash_balance FROM accounts;
SELECT symbol, COUNT(*) FROM orders GROUP BY symbol;
```

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

#### Reset Test Database
```bash
# Drop and recreate test database
psql -h localhost -p 5433 -U postgres -c "DROP DATABASE IF EXISTS leap_trading_test;"
psql -h localhost -p 5433 -U postgres -c "CREATE DATABASE leap_trading_test;"
```

---

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

### IDE Setup

#### IntelliJ IDEA
1. Open project: `File → Open → backend/trading-platform`
2. Configure JDK: `Project Structure → SDK → Java 21`
3. Enable annotation processing: `Settings → Build → Annotation Processors → Enable`
4. Run: `Run → Run 'TradingPlatformApplication'`

#### VS Code
```bash
# Install extensions:
# - Extension Pack for Java (Microsoft)
# - Spring Boot Extension Pack (Pivotal)

# Open folder:
code backend/trading-platform

# Run via command palette:
Ctrl+Shift+P → Spring Boot: Run
```

### Common Development Tasks

#### Add a New Entity
```java
// 1. Create entity class in appropriate package
@Entity
@Table(name = "my_table")
public class MyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "my_column")
    private String myField;
}

// 2. Create repository
public interface MyEntityRepository extends JpaRepository<MyEntity, Long> {
    Optional<MyEntity> findByMyField(String myField);
}

// 3. Create migration
// File: src/main/resources/db/migration/VXX__add_my_table.sql
CREATE TABLE my_table (
    id BIGSERIAL PRIMARY KEY,
    my_column VARCHAR(255)
);

// 4. Restart application (migrations run automatically)
```

#### Add a New REST Endpoint
```java
@RestController
@RequestMapping("/api/my-endpoint")
public class MyController {
    
    @GetMapping
    public List<MyDTO> getAll() {
        return myService.getAll();
    }
    
    @PostMapping
    public ResponseEntity<MyDTO> create(@RequestBody MyRequest req) {
        MyDTO created = myService.create(req);
        return ResponseEntity.status(201).body(created);
    }
}
```

#### Add a New Service
```java
@Service
@Transactional
public class MyService {
    
    private final MyEntityRepository repository;
    
    public MyService(MyEntityRepository repository) {
        this.repository = repository;
    }
    
    public List<MyEntity> getAll() {
        return repository.findAll();
    }
}
```

### Code Conventions

- **Naming**: PascalCase for classes, camelCase for variables/methods
- **Packages**: Group by domain (auth, order, portfolio, etc.)
- **Logging**: Use SLF4J via `@Slf4j` annotation
- **Comments**: Document public methods, complex logic, and business rules
- **Error Handling**: Create custom exceptions extending `RuntimeException`

### Running with Debug Mode
```bash
# Via Maven
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"

# Then attach debugger to localhost:5005
```

---

## Testing

### Test Structure

```
src/test/java/com/goatteen/trading/
├── execution/
│   ├── IdempotencyKeyUnitTest.java              # 7 tests
│   ├── IdempotencyServiceTest.java              # 14 tests
│   ├── OrderExecutionIdempotencyTest.java       # 9 tests
│   └── OrderExecutionServiceTest.java
├── order/
│   ├── OrderValidationServiceTest.java          # 6 tests
│   └── controller/OrderControllerTest.java      # 4 tests
├── config/
│   └── PasswordEncoderTest.java                 # 2 tests
└── ...
```

### Running Tests

```bash
# Run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=IdempotencyKeyUnitTest

# Run tests matching pattern
mvn test -Dtest=*Idempotency*

# Run with coverage report
mvn clean test jacoco:report

# View coverage: target/site/jacoco/index.html
```

### Test Summary
- **Total Tests**: 47 (5 skipped)
- **Coverage**: Core business logic (order execution, idempotency, auth)
- **Frameworks**: JUnit 5, Mockito 5.x
- **Profiles**: Tests run with `@ActiveProfiles("test")`

### Writing Tests

#### Unit Test Example
```java
@DisplayName("Idempotency Key Tests")
class IdempotencyKeyUnitTest {
    
    @Mock
    private FillRepository fillRepository;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    @DisplayName("Should return existing fill for duplicate key")
    void testDuplicateDetection() {
        // Arrange
        String key = "dup-key-123";
        Fill existing = new Fill();
        when(fillRepository.findByIdempotencyKey(key))
            .thenReturn(Optional.of(existing));
        
        // Act
        Optional<Fill> result = fillRepository.findByIdempotencyKey(key);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(existing, result.get());
    }
}
```

### Debugging Tests
```bash
# Run single test with debug output
mvn -e test -Dtest=IdempotencyKeyUnitTest -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

---

## Deployment

### Build for Production

```bash
# Clean production build
mvn clean package -DskipTests -Pprod

# Verify JAR
jar tf target/trading-platform-0.0.1-SNAPSHOT.jar | head -20
```

### Docker Deployment

#### Build Docker Image
```bash
# Using Dockerfile in project root
docker build -f Dockerfile -t trading-platform:latest .

# Verify image
docker images | grep trading-platform
```

#### Run in Docker
```bash
# Use your deployment platform's secret management for credentials
# This example shows proper pattern with environment variable injection:

docker run -d \
  --name trading-platform \
  -p 8080:8080 \
  --env-file /path/to/.env.prod \
  -e SPRING_PROFILES_ACTIVE=prod \
  trading-platform:latest

# View logs
docker logs -f trading-platform
```

> Provide database URL, username, and other sensitive config through your deployment system's secret management, not in command line.

### Kubernetes Deployment (if applicable)
```bash
# Apply deployment manifest
kubectl apply -f k8s/deployment.yaml

# Check status
kubectl get pods -l app=trading-platform
kubectl logs -f pod/<pod-name>
```

### Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Detailed metrics
curl http://localhost:8080/actuator/metrics

# Database connectivity
curl http://localhost:8080/actuator/health/db
```

### Monitoring & Logs

```bash
# View application logs (dev mode)
mvn spring-boot:run

# View application logs (production)
docker logs -f trading-platform

# SQL query logging
# Set in application.yaml:
# spring.jpa.show-sql: true
# spring.jpa.properties.hibernate.format_sql: true
```

---

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

### Tests Failing

#### "ApplicationContext failure threshold exceeded"
```
Cause: Database connection issues in tests
Solution:
1. Ensure database is running
2. Check application-test.yaml configuration
3. Run specific test: mvn test -Dtest=TestName
4. View full error: mvn -e test -Dtest=TestName
```

#### "Cannot find symbol" during compilation
```bash
# Clean and rebuild
mvn clean compile

# Update IDE: Ctrl+Shift+O (organize imports)
```

### Performance Issues

#### Slow Query Execution
```yaml
# Enable SQL logging to identify slow queries
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        generate_statistics: true
        use_sql_comments: true
```

#### High Memory Usage
```bash
# Increase heap size
export JAVA_OPTS="-Xmx2G -Xms1G"
mvn spring-boot:run
```

---

## Key Features Deep Dive

### Idempotent Order Execution

**Problem**: Duplicate fills when order execution request is retried (network error, timeout, etc.)

**Solution**: Three-layer protection
1. **Client Layer** (Angular): Generates UUID, stores in sessionStorage, sends with each request
2. **HTTP Layer**: `Idempotency-Key` header
3. **Backend Layer**: Database constraint + service-level duplicate detection

**Implementation**:
```java
// OrderExecutionService.java
public Fill executeOrder(Long orderId, String idempotencyKey) {
    // 1. Check if duplicate
    Optional<Fill> existingFill = fillRepository.findByIdempotencyKey(idempotencyKey);
    if (existingFill.isPresent()) {
        return existingFill.get();  // Return original fill
    }
    
    // 2. Lock order for exclusive execution
    Order order = orderRepository.findByIdForUpdate(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
    
    // 3. Validate state and execute atomically
    order.validateTransitionToFilled();
    Fill fill = executeAtomically(order, idempotencyKey);
    
    return fill;  // Persisted with unique idempotency key
}
```

**Test Coverage**:
- ✅ Duplicate detection (returns existing fill)
- ✅ Pessimistic locking (prevents concurrent execution)
- ✅ Persistent key storage (survives restarts)

### Pessimistic Locking

**Prevents**: Concurrent execution of the same order

**Implementation**:
```java
// OrderRepository.java
@Query("SELECT o FROM Order o WHERE o.id = :id")
@Lock(LockModeType.PESSIMISTIC_WRITE)  // Database-level write lock
Optional<Order> findByIdForUpdate(@Param("id") Long id);
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

---

## FAQ

**Q: How do I add a new database migration?**
A: Create a new file `V{XX}__description.sql` in `src/main/resources/db/migration/` following Flyway naming convention. Flyway runs it automatically on startup.

**Q: Can I modify existing migrations?**
A: No. Flyway prevents this. Create a new migration to fix issues (e.g., V15__fix_previous_issue.sql).

**Q: How do I check if an order will be deduplicated?**
A: Check the `idempotency_key` column: `SELECT * FROM fills WHERE idempotency_key = 'your-key';`

**Q: What happens if I submit the same order twice with different keys?**
A: Two separate fills will be created (each with unique key). Keys are for deduplicating retries of the *same* request.

**Q: How do I access the production database?**
A: Use your deployment platform's secret management system (Kubernetes Secrets, AWS Secrets Manager, Vault, etc.). Never commit credentials to version control.

---

## Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/3.5.5/reference/html/)
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Hibernate Documentation](https://docs.jboss.org/hibernate/orm/6.6/userguide/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/17/)
- [Flyway Documentation](https://documentation.red-gate.com/display/Flyway)
- [JWT Guide](https://tools.ietf.org/html/rfc7519)

---

## Support

For issues or questions:
1. Check this README and existing documentation
2. Search existing GitHub issues
3. Create new GitHub issue with detailed description
4. Contact the team on Slack: #trading-platform-dev

---

**Last Updated**: October 1, 2026  
**Version**: 0.0.1-SNAPSHOT  
**Status**: 🟢 Production Ready (with test coverage)
