# Market Data Service

A Spring Boot microservice that provides simulated market data across multiple asset classes and geographic markets. This service powers the trading platform's quote feeds with dynamic, evolving price data.

## Overview

The Market Data Service is responsible for:
- **Simulating real-time market data** with a random walk price evolution model
- **Serving market quotes** across US, UK, India, Crypto, and Forex markets
- **Filtering and retrieving data** by market, asset type, or individual symbol
- **Auto-updating prices** on a scheduled interval (every 3 seconds)

This is a **mock service** designed for development and testing. It loads static instrument definitions from CSV and simulates price movements in real-time without external API dependencies.

## Project Structure

```
backend/market-data-service/
├── pom.xml                          # Maven configuration & dependencies
├── mvnw / mvnw.cmd                  # Maven wrapper scripts (Windows/Unix)
├── src/
│   ├── main/
│   │   ├── java/com/goatteen/market/
│   │   │   ├── MarketDataApplication.java    # Spring Boot entry point
│   │   │   ├── controller/
│   │   │   │   └── MarketDataController.java # REST API endpoints
│   │   │   ├── service/
│   │   │   │   └── MockMarketDataService.java # Core business logic
│   │   │   ├── loader/
│   │   │   │   └── MarketDataCsvLoader.java  # CSV parsing & loading
│   │   │   └── dto/
│   │   │       └── MarketData.java           # Data Transfer Object
│   │   └── resources/
│   │       ├── application.yml               # Spring configuration
│   │       └── market-data.csv               # Instrument definitions
│   └── test/
│       └── (test sources)
└── target/                          # Maven build output
```

## Folder & File Descriptions

### `/java/com/goatteen/market/`

**MarketDataApplication.java**
- Spring Boot application entry point
- Enables scheduling with `@EnableScheduling` for price update interval
- Main method starts the service on configured port (default: 8081)

### `/controller`

**MarketDataController.java**
- REST API endpoints for querying market data
- **Endpoints:**
  - `GET /api/market-data` - All market data across all symbols
  - `GET /api/market-data/market/{market}` - Data filtered by market (US, UK, INDIA, CRYPTO, FOREX)
  - `GET /api/market-data/symbol/{symbol}` - Single symbol data (e.g., AAPL, BTC, EURUSD)

### `/service`

**MockMarketDataService.java**
- Core business logic for market simulation
- **Key responsibilities:**
  - Loads initial data from CSV on startup via `@PostConstruct`
  - Runs scheduled price updates every 3 seconds
  - Applies random walk algorithm: prices evolve from current price ±5% max
  - Stores data in thread-safe `ConcurrentHashMap<String, MarketData>`
  - Provides query methods: `getMarketData()`, `getMarketDataByMarket()`, `getAllMarketData()`
- **Key methods:**
  - `loadData()` - Load CSV on startup
  - `updateMarketPrices()` - Scheduled price updates (runs every 3 seconds)
  - `generateNextPrice()` - Calculate next price from current using random walk
  - `copyMarketData()` - Return immutable copy of data to prevent cache tampering

### `/loader`

**MarketDataCsvLoader.java**
- Parses CSV file using OpenCSV library
- Loads instrument definitions with:
  - Symbol (e.g., AAPL, RELIANCE.NS)
  - Asset type (STOCK, CRYPTO, FOREX)
  - Country/market (US, UK, INDIA)
  - Currency (USD, GBP, INR)
  - Initial price data
- Handles malformed/blank rows gracefully
- Throws `IllegalStateException` if CSV not found

### `/dto`

**MarketData.java**
- Data Transfer Object representing a single quote
- **Fields:**
  ```
  assetType    - Type of instrument (STOCK, CRYPTO, FOREX)
  symbol       - Trading symbol (AAPL, BTC, EURUSD)
  exchange     - Exchange name (NYSE, LSE, NSE)
  countryCode  - Country (US, UK, INDIA)
  currency     - Quote currency (USD, GBP, INR)
  market       - (Backwards compatible field, mirrors countryCode)
  name         - Human-readable name
  price        - Current quote price
  change       - Absolute price change since last update
  changePercent - Percentage price change
  high         - Highest price in simulation window
  low          - Lowest price in simulation window
  volume       - Trading volume (simulated)
  timestamp    - Quote timestamp
  ```

### `/resources`

**application.yml**
- Spring Boot configuration
- **Key settings:**
  - `spring.application.name: market-data-service`
  - `server.port: 8081`
  - Logging levels (DEBUG for com.goatteen.market)

**market-data.csv**
- CSV file with instrument definitions
- Loaded on startup to initialize simulated market
- Format: `symbol,assetType,exchange,countryCode,currency,name,price,high,low,volume`
- Example entries:
  ```
  AAPL,STOCK,NYSE,US,USD,Apple Inc.,150.00,152.50,148.00,1000000
  RELIANCE.NS,STOCK,NSE,INDIA,INR,Reliance Industries,2500.00,2550.00,2450.00,500000
  BTC,CRYPTO,BINANCE,GLOBAL,USD,Bitcoin,42000.00,43000.00,41000.00,100
  EURUSD,FOREX,FOREX,GLOBAL,USD,Euro/USD,1.10,1.12,1.08,1000000
  ```

## Core Workflows

### 1. Service Startup

```
MarketDataApplication starts
  ↓
Spring initializes MarketDataService
  ↓
@PostConstruct: loadData() executes
  ├─ MarketDataCsvLoader reads market-data.csv
  ├─ Parse each instrument into MarketData object
  └─ Cache in ConcurrentHashMap
  ↓
@Scheduled(fixedRate=3000) begins
  ├─ Every 3 seconds: updateMarketPrices()
  └─ Replaces all prices with randomWalk(currentPrice)
```

### 2. Query Workflow

```
HTTP Request: GET /api/market-data/symbol/AAPL
  ↓
MarketDataController.getMarketData("AAPL")
  ↓
MockMarketDataService.getMarketData("AAPL")
  ├─ Lookup in marketDataCache
  ├─ Return defensive copy (copyMarketData)
  └─ Thread-safe, doesn't expose internal cache
  ↓
JSON response with current price, change, timestamp
```

### 3. Price Evolution Workflow

```
Current state: AAPL = $150.00
  ↓
updateMarketPrices() fires (every 3 seconds)
  ↓
For each symbol, generateNextPrice(current):
  ├─ Calculate random volatility (-5% to +5%)
  ├─ Apply: newPrice = currentPrice * (1 + volatility)
  └─ Round to 2 decimal places
  ↓
Updated price in cache: AAPL = $150.75
  ↓
Next client query gets $150.75
```

## API Endpoints

### Get All Market Data

```
GET /api/market-data
```

Returns all instruments across all markets, sorted by symbol.

**Response (200 OK):**
```json
[
  {
    "assetType": "STOCK",
    "symbol": "AAPL",
    "exchange": "NYSE",
    "countryCode": "US",
    "currency": "USD",
    "market": "US",
    "name": "Apple Inc.",
    "price": 150.42,
    "change": 0.42,
    "changePercent": 0.28,
    "high": 152.50,
    "low": 148.00,
    "volume": 1000000,
    "timestamp": "2024-10-01T14:30:00"
  },
  ...
]
```

### Get Market Data by Market

```
GET /api/market-data/market/{market}
```

Filter by market (US, UK, INDIA, CRYPTO, FOREX).

**Example:**
```
GET /api/market-data/market/UK
```

**Response (200 OK):**
```json
[
  {
    "symbol": "HSBA.L",
    "exchange": "LSE",
    "countryCode": "UK",
    "currency": "GBP",
    "price": 540.50,
    ...
  },
  ...
]
```

### Get Specific Symbol

```
GET /api/market-data/symbol/{symbol}
```

Fetch single instrument by symbol (case-insensitive).

**Example:**
```
GET /api/market-data/symbol/EURUSD
```

**Response (200 OK):**
```json
{
  "assetType": "FOREX",
  "symbol": "EURUSD",
  "exchange": "FOREX",
  "countryCode": "GLOBAL",
  "currency": "USD",
  "market": "GLOBAL",
  "name": "Euro/USD",
  "price": 1.0947,
  "change": 0.0047,
  "changePercent": 0.43,
  "high": 1.12,
  "low": 1.08,
  "volume": 1000000,
  "timestamp": "2024-10-01T14:30:00"
}
```

**Response (404 Not Found):**
```
null (when symbol doesn't exist)
```

## Setup & Running

### Prerequisites

- **Java 21+**
- **Maven 3.8+**
- Port 8081 available (or configure in `application.yml`)

### Build

```bash
cd backend/market-data-service

# Compile and package
mvn clean package

# Or just compile
mvn clean compile
```

### Run

```bash
# Using Maven
mvn spring-boot:run

# Or from JAR after mvn package
java -jar target/market-data-service-0.0.1-SNAPSHOT.jar
```

Service starts on `http://localhost:8081`

### Verify Running

```bash
# Check service health
curl http://localhost:8081/api/market-data/symbol/AAPL

# Get all instruments
curl http://localhost:8081/api/market-data

# Get India market
curl http://localhost:8081/api/market-data/market/INDIA
```

## Configuration

### Application Properties (application.yml)

```yaml
spring:
  application:
    name: market-data-service

server:
  port: 8081                    # Service port

logging:
  level:
    root: INFO                  # Root logging level
    com.goatteen.market: DEBUG  # Package-specific debug
```

### Price Update Interval

In `MockMarketDataService.updateMarketPrices()`:
```java
@Scheduled(fixedRate = 3000)  // 3 seconds in milliseconds
```

Change value to adjust update frequency. Values in milliseconds.

### CSV File Location

Default: `src/main/resources/market-data.csv`

Location is configured in `MarketDataCsvLoader.CSV_FILE_PATH`. Place updated CSV here and rebuild.

## Architecture & Design Decisions

### Thread Safety

- **ConcurrentHashMap** used for price cache to handle concurrent updates and reads
- Price updates happen on scheduler thread, queries on HTTP threads
- `copyMarketData()` returns defensive copies to prevent external modification

### Price Simulation

- **Random Walk Model**: Prices evolve from current price, not reset from CSV each cycle
  - Creates realistic price evolution
  - Each symbol has independent random seed
  - Volatility capped at ±5% per update
  
- **Why Mock?**
  - No external API dependencies (Finnhub/Alpha Vantage)
  - Deterministic for testing
  - Full control over market state

### Stateless Service

- No database persistence
- All state in-memory cache
- Service restart reloads from CSV
- No inter-request coupling

### Backward Compatibility

- `market` field mirrors `countryCode` for compatibility with existing Angular UI
- New filtering code should use `assetType`
- Legacy code can be migrated over time

## Development

### Adding New Markets

1. Add instruments to `market-data.csv`
2. Restart service (or hot-reload if enabled)
3. CSV is automatically loaded on startup

### Adding New Fields to MarketData

1. Add field to `MarketData.java` with getters/setters
2. Add CSV column and parsing logic to `MarketDataCsvLoader.java`
3. Update column indices in CSV parsing
4. Restart service

### Testing Locally

```bash
# Terminal 1: Start service
mvn spring-boot:run

# Terminal 2: Monitor price changes
while true; do curl -s http://localhost:8081/api/market-data/symbol/AAPL | jq '.price, .timestamp'; sleep 2; done
```

## Dependencies

- **Spring Boot 3.5.5** - Web framework & scheduling
- **OpenCSV 5.8** - CSV parsing
- **Lombok** - Boilerplate reduction
- **Jakarta Persistence** - JPA annotations (included with Spring Boot)

See `pom.xml` for full dependency list and versions.

## Integration with Trading Platform

The Market Data Service is used by:
- **Trading Platform** (`backend/trading-platform`):
  - Fetches quotes via REST calls to `/api/market-data/symbol/{symbol}`
  - Used in `OrderExecutionService` to determine execution price
  - Quote stored in Fill entity for audit trail
  
- **Future Use** (Sprint 6 Analytics):
  - Dashboard queries for volume, trends, market data
  - Historical data retrieval from simulation state

## Performance Notes

- **Price cache:** O(1) lookup by symbol
- **Market filtering:** O(n) stream filter (small dataset)
- **CSV loading:** Single-threaded, happens once at startup
- **Price updates:** Non-blocking scheduled task, doesn't block HTTP threads
- **Query latency:** Typically <10ms for single symbol, <50ms for market filter

## Troubleshooting

### Service won't start

```
ERROR: Failed to load market-data.csv
```
**Solution:** Ensure `market-data.csv` exists in `src/main/resources/`

### All prices are NaN

```
Caused by: java.lang.NumberFormatException: For input string: "invalid"
```
**Solution:** Check CSV format - ensure price columns are numeric

### Port 8081 already in use

**Solution:** Change port in `application.yml` or kill process:
```bash
# Windows
netstat -ano | findstr :8081
taskkill /PID <PID> /F

# Mac/Linux
lsof -i :8081
kill -9 <PID>
```

### Spring beans not autowiring

**Solution:** Ensure `@SpringBootApplication` annotation is present on main class and service classes use `@Service`, `@Component`, `@Repository` annotations.

---

**Service Version:** 0.0.1-SNAPSHOT  
**Last Updated:** October 2026  
**Maintainer:** GOATeen Trading Platform Team
