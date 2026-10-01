# Frontend - GOATeen Trading Platform UI

A modern Angular 22 single-page application (SPA) providing a user-facing interface for the GOATeen trading platform. The frontend handles user authentication, account management, market data visualization, and order placement with built-in resilience patterns.

## Overview

The GOATeen Trading Platform UI is built with:
- **Angular 22** - Latest standalone components & signals API
- **RxJS 7.8** - Reactive programming & async operations
- **TypeScript 6.0** - Strongly-typed development
- **Standalone Components** - Modern Angular architecture (no NgModules)

The application provides:
- **Authentication** - JWT-based login/register with refresh tokens
- **Dashboard** - Real-time market data, positions, and order management
- **Resizable Panels** - Custom drag-to-resize UI components
- **Order Management** - Place, execute, and track orders
- **Account Views** - Holdings, cash balance, and transaction history
- **Error Handling** - User-friendly notifications and error recovery

## Table of Contents

- [Project Structure](#project-structure)
- [Folder & File Descriptions](#folder--file-descriptions)
  - [Root Level](#root-level)
  - [/core - Shared Infrastructure](#core---shared-infrastructure)
  - [/dashboard - Main Trading Interface](#dashboard---main-trading-interface)
  - [/features/auth - Auth Pages](#featuresauth---auth-pages)
  - [/environments - Environment Configuration](#environments---environment-configuration)
  - [Root Files](#root-files)
- [Core Workflows](#core-workflows)
- [API Endpoints](#api-endpoints)
- [Setup & Running](#setup--running)
- [Architecture & Design Patterns](#architecture--design-patterns)
- [Resilience Patterns](#resilience-patterns)
- [Dependencies](#dependencies)
- [Integration with Backend](#integration-with-backend)
- [Performance Optimization](#performance-optimization)
- [Troubleshooting](#troubleshooting)
- [Testing Strategy](#testing-strategy)
- [Project Standards](#project-standards)

## Project Structure

```
frontend/
├── src/
│   ├── app/
│   │   ├── app.ts                           # Root component
│   │   ├── app.html                         # Root template
│   │   ├── app.routes.ts                    # Route definitions
│   │   ├── app.config.ts                    # Angular configuration & providers
│   │   ├── app.scss                         # Global styles
│   │   ├── app.spec.ts                      # Root tests
│   │   ├── core/                            # Shared utilities (auth, services, guards)
│   │   │   ├── auth/
│   │   │   │   ├── auth.ts                  # Auth service (login, token management)
│   │   │   │   └── auth.spec.ts             # Auth service tests
│   │   │   ├── services/
│   │   │   │   ├── order.ts                 # Order placement & execution
│   │   │   │   ├── account.ts               # Account & holdings queries
│   │   │   │   ├── market-data.ts           # Instrument & quote data
│   │   │   │   ├── notification.ts          # Toast/notification service
│   │   │   │   ├── idempotency.ts           # Request deduplication
│   │   │   │   ├── *.spec.ts                # Service unit tests
│   │   │   ├── guards/
│   │   │   │   ├── auth-guard.ts            # Protects private routes
│   │   │   │   └── auth-guard.spec.ts       # Guard tests
│   │   │   └── interceptors/
│   │   │       ├── jwt-interceptor.ts       # Attaches auth tokens
│   │   │       ├── idempotency-interceptor.ts  # Adds idempotency headers
│   │   │       └── jwt-interceptor.spec.ts  # Interceptor tests
│   │   ├── dashboard/
│   │   │   ├── dashboard.component.ts       # Main trading interface
│   │   │   ├── dashboard.component.html     # Dashboard template
│   │   │   ├── dashboard.component.scss     # Dashboard styles
│   │   │   ├── dashboard.component.spec.ts  # Dashboard tests
│   │   │   └── resizable.directive.ts       # Drag-to-resize panels
│   │   └── features/
│   │       └── auth/
│   │           ├── login/
│   │           │   ├── login.ts             # Login page component
│   │           │   ├── login.html           # Login form
│   │           │   ├── login.scss           # Login styles
│   │           │   └── login.spec.ts        # Login tests
│   │           └── register/
│   │               ├── register.ts          # Registration page
│   │               ├── register.html        # Registration form
│   │               ├── register.scss        # Registration styles
│   │               └── register.spec.ts     # Registration tests
│   ├── environments/
│   │   ├── environment.ts                   # Dev environment config
│   │   └── environment.prod.ts              # Production config
│   ├── index.html                           # Entry HTML file
│   ├── main.ts                              # Bootstrap file
│   └── styles.scss                          # Global SCSS variables/mixins
├── public/                                  # Static assets
├── angular.json                             # Angular CLI configuration
├── package.json                             # NPM dependencies & scripts
├── tsconfig.json                            # TypeScript base config
├── tsconfig.app.json                        # TypeScript app config
├── tsconfig.spec.json                       # TypeScript test config
└── README.md                                # This file
```

## Folder & File Descriptions

### Root Level

**app.ts**
- Root Angular component (standalone)
- Contains `<router-outlet>` for route rendering
- Signals API for reactive title management

**app.routes.ts**
- Route definitions for the entire application
- Routes:
  - `/login` - Login page (public)
  - `/register` - Registration page (public)
  - `/dashboard` - Main trading interface (protected by authGuard)
  - `` (default) - Redirects to login
  - `**` (wildcard) - Redirects to login

**app.config.ts**
- Angular application configuration
- Provides router and HTTP client
- Registers HTTP interceptors (JWT, idempotency)

**app.scss**
- Global styles shared across all components
- SCSS variables, mixins, and utility classes
- Baseline typography and layout rules

**app.spec.ts**
- Unit tests for root component
- Tests signal values and component initialization

### `/core` - Shared Infrastructure

The core folder contains singleton services and utilities used throughout the app.

**auth/ - Authentication & Authorization**

- **auth.ts**
  - `Auth` service for login/logout/token management
  - Methods:
    - `login(LoginRequest)` → Observable<LoginResponse> with accessToken
    - `saveToken(token)` → localStorage storage
    - `getToken()` → Retrieve stored token
    - `clearToken()` → Remove token on logout
    - `refreshToken()` → Refresh expired access tokens (via refresh token in cookie)
  - Uses `withCredentials: true` for HTTP-only cookie refresh tokens
  - Interfaces:
    - `LoginRequest` - email & password
    - `LoginResponse` - accessToken
    - `RefreshResponse` - new accessToken

- **auth.spec.ts**
  - Unit tests for Auth service
  - Mocks HttpClient
  - Tests login flow, token storage, token refresh

**services/ - Business Logic Services**

- **order.ts**
  - `OrderService` for order operations
  - Methods:
    - `placeOrder(PlaceOrderRequest)` → Place new order
    - `executeOrder(orderId)` → Execute pending order with idempotency
  - Interfaces:
    - `PlaceOrderRequest` - instrumentId, side (BUY/SELL), quantity
    - `OrderResponse` - full order details with status
  - Relies on `OrderSide` type union: 'BUY' | 'SELL'

- **account.ts**
  - `AccountService` for account data
  - Methods:
    - `getAccount()` → Current user's account details
    - `getHoldings()` → Current positions & quantities
    - `getOrders()` → Order history
    - `getTransactions()` → Cash transaction history
  - Interfaces:
    - `AccountResponse` - accountId, cashBalance, currency
    - `HoldingsResponse` - positions with instrument & quantity
    - `OrderResponse` - order status, side, quantity, fill price
    - `TransactionResponse` - cash movements with timestamps

- **market-data.ts**
  - `MarketDataService` for instrument & quote data
  - Methods:
    - `getInstruments()` → All tradable instruments
    - `getLatestQuote(instrumentId)` → Real-time bid/ask/last prices
  - Interfaces:
    - `MarketInstrumentResponse` - instrument metadata & latest quote
    - `LatestQuoteResponse` - quote prices with timestamp

- **notification.ts**
  - `NotificationService` for user-facing messages
  - Methods:
    - `success(message)` → Success toast
    - `error(message)` → Error toast
    - `info(message)` → Info notification
  - Used by components to provide feedback

- **idempotency.ts**
  - Helper service for idempotency key generation
  - Methods:
    - `generateIdempotencyKey()` → UUID v4 for request deduplication
  - Used by interceptor to add `Idempotency-Key` header

**guards/ - Route Protection**

- **auth-guard.ts**
  - `authGuard` function (functional guard pattern)
  - Protects routes requiring authentication
  - Checks for valid token in localStorage
  - Redirects to login if token missing/invalid
  - Used on `/dashboard` route

**interceptors/ - HTTP Middleware**

- **jwt-interceptor.ts**
  - Adds JWT authorization header to all requests
  - Retrieves token from Auth service
  - Appends: `Authorization: Bearer <token>`
  - Handles token refresh if 401 response

- **idempotency-interceptor.ts**
  - Adds idempotency header for mutation requests (POST)
  - Generates unique ID per request
  - Adds: `Idempotency-Key: <uuid>`
  - Prevents duplicate order execution if requests are retried

### `/dashboard` - Main Trading Interface

**dashboard.component.ts**
- Main trading interface component
- Manages resizable panel layout using signals
- Signals:
  - `chartAreaWidth` - Chart panel width
  - `orderPanelHeight` - Order placement panel height
  - `positionsPanelHeight` - Positions panel height
- Lifecycle:
  - `ngOnInit()` - Fetch account, holdings, instruments, quotes
  - `ngOnDestroy()` - Cleanup subscriptions
- Uses `forkJoin` to load multiple data sources in parallel

**dashboard.component.html**
- Responsive layout with resizable panels
- Sections:
  - Market data table (all instruments)
  - Order placement form
  - Current positions / holdings
  - Account summary
- Drag handles on panel borders for resizing

**dashboard.component.scss**
- Dashboard-specific styles
- Flex layout for panels
- Responsive breakpoints

**dashboard.component.spec.ts**
- Tests component initialization
- Tests data loading
- Tests panel resizing

**resizable.directive.ts**
- Custom `appResizable` directive for drag-to-resize
- Uses `angular-resizable-element` library
- Emits `resizeEnd` event with new dimensions
- Applied to panel dividers in template

### `/features/auth` - Auth Pages

**features/auth/login/ - Login Page**

- **login.ts**
  - Login form component (standalone)
  - Form fields: email, password
  - Calls `Auth.login()` on submit
  - Stores token via `Auth.saveToken()`
  - Navigates to dashboard on success
  - Shows error notifications on failure

- **login.html**
  - Email input field
  - Password input field
  - Submit button
  - Link to register page

- **login.scss**
  - Form styling
  - Input field styles
  - Button styles
  - Error message styling

**features/auth/register/ - Registration Page**

- **register.ts**
  - Registration form component
  - Form fields: email, password, confirm password
  - Validates passwords match
  - Calls backend registration endpoint
  - Redirects to login on success

- **register.html**
  - Email input
  - Password input
  - Confirm password input
  - Submit button
  - Link back to login

### `/environments` - Environment Configuration

**environment.ts (Development)**
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'
};
```

**environment.prod.ts (Production)**
```typescript
export const environment = {
  production: true,
  apiUrl: 'https://api.example.com/api'
};
```

Used in services to determine API endpoint URLs.

### Root Files

**angular.json**
- Angular CLI configuration
- Build targets (dev, prod, test)
- Project paths
- SCSS preprocessor settings

**package.json**
- NPM dependencies
- Build/serve/test scripts
- Version info

**tsconfig.json**
- TypeScript base configuration
- Compiler options

**tsconfig.app.json**
- App-specific TypeScript config
- Includes app source files

**tsconfig.spec.json**
- Test-specific TypeScript config
- Includes test files

## Core Workflows

### 1. Application Bootstrap

```
main.ts bootstrap
  ↓
Angular loads app.ts (root component)
  ↓
app.config.ts provides:
  ├─ Router (from app.routes.ts)
  └─ HttpClient with interceptors:
      ├─ jwtInterceptor (adds auth)
      └─ idempotencyInterceptor (adds idempotency key)
  ↓
Router activates route (default = /login)
  ↓
Login page displayed
```

### 2. Login & Authentication Flow

```
User enters credentials on login.html
  ↓
login.ts calls Auth.login(request)
  ↓
jwtInterceptor (HTTP middleware):
  ├─ Checks for token
  └─ None exists yet (first login)
  ↓
Backend returns accessToken
  ↓
Auth.saveToken(token) stores in localStorage
  ↓
Router navigates to /dashboard
```

### 3. Dashboard Data Load

```
User navigates to /dashboard
  ↓
authGuard checks:
  ├─ Token exists in localStorage? YES
  └─ Allows navigation
  ↓
dashboard.component.ts ngOnInit():
  ├─ AccountService.getAccount()
  ├─ AccountService.getHoldings()
  ├─ MarketDataService.getInstruments()
  └─ All run in parallel via forkJoin
  ↓
HTTP requests made:
  ├─ All include Authorization header (jwtInterceptor)
  ├─ All include Idempotency-Key header (idempotencyInterceptor)
  ↓
Backend validates token, processes requests
  ↓
Dashboard displays:
  ├─ Account balance
  ├─ Market instruments table
  └─ Current positions
```

### 4. Placing & Executing Order

```
User fills order form:
  ├─ Selects instrument
  ├─ Chooses BUY/SELL
  └─ Enters quantity
  ↓
User clicks "Place Order"
  ↓
login.ts calls OrderService.placeOrder(request)
  ↓
Interceptors attach:
  ├─ Authorization header (token)
  └─ Idempotency-Key (unique ID)
  ↓
Backend creates Order (status=SUBMITTED)
  ↓
Frontend shows confirmation
  ↓
User clicks "Execute"
  ↓
OrderService.executeOrder(orderId)
  ↓
Interceptor sends SAME Idempotency-Key
  (allows safe retry if network fails)
  ↓
Backend transitions Order (SUBMITTED → FILLED)
  ↓
Dashboard updates with filled order details
```

### 5. Panel Resizing

```
User clicks & drags panel divider
  ↓
resizable.directive detects drag
  ↓
Directive emits resizeEnd(event) to component
  ↓
Component receives onResizeEnd(event):
  ├─ Extracts new width/height from event.edges
  └─ Updates signal: this.chartAreaWidth.set(newWidth)
  ↓
Signal update triggers template re-render
  ↓
Panel size changes in UI
```

## API Endpoints

Services call these backend endpoints (via `environment.apiUrl`):

### Authentication (`/auth`)
- `POST /auth/login` - Login with email/password
- `POST /auth/refresh` - Refresh access token (uses refresh token cookie)

### Accounts (`/accounts`)
- `GET /accounts/{id}` - Get account details
- `GET /accounts/{id}/holdings` - Get current positions
- `GET /accounts/{id}/orders` - Get order history
- `GET /accounts/{id}/transactions` - Get cash transaction history

### Orders (`/orders`)
- `POST /orders` - Place new order
- `POST /orders/execute/{id}` - Execute pending order

### Market (`/market`)
- `GET /market/instruments` - Get all tradable instruments
- `GET /market/instruments/{id}/quote` - Get latest quote for instrument

## Setup & Running

### Prerequisites

- **Node.js 18+** (includes npm)
- **Angular CLI 22+**

### Installation

```bash
cd frontend

# Install dependencies
npm install
```

### Development Server

```bash
# Start dev server on port 4200
ng serve

# Or use npm script shortcut
npm start
```

Navigate to `http://localhost:4200/` in browser. App auto-reloads on file changes.

### Configuration

**Backend URL:**
Edit `src/environments/environment.ts`:
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'  // Change this
};
```

### Building for Production

```bash
# Build optimized bundle
ng build --configuration production
# or
npm run build

# Output: dist/goatteen-trading-ui/
```

### Running Tests

```bash
# Run unit tests with Vitest
ng test
# or
npm test

# Watch mode
ng test --watch
```

### Code Quality

```bash
# Format code with Prettier
npx prettier --write src/
```

## Architecture & Design Patterns

### Standalone Components

Modern Angular uses standalone components instead of NgModules:
```typescript
@Component({
  imports: [CommonModule, FormsModule],  // Explicit imports
  selector: 'app-root',
  standalone: true,
  templateUrl: './app.html',
})
export class App { }
```

**Benefits:**
- Simpler mental model
- Tree-shakeable unused code
- Better scalability

### Signals API

Reactive state management using Angular Signals:
```typescript
chartAreaWidth = signal(600);  // Initial value

// In template: {{ chartAreaWidth() }}
// Update: this.chartAreaWidth.set(800)
// Computed: const isTall = computed(() => this.chartAreaWidth() > 500)
```

**Benefits:**
- Fine-grained reactivity (only affected components re-render)
- Better performance than zone.js
- Simpler than observables for component state

### HTTP Interceptors

Middleware pattern for HTTP requests:
- **JWT Interceptor** - Adds authorization tokens automatically
- **Idempotency Interceptor** - Prevents duplicate mutations

```typescript
withInterceptors([jwtInterceptor, idempotencyInterceptor])
```

### Functional Routing Guards

Modern Angular uses functional guards instead of classes:
```typescript
export const authGuard: CanActivateFn = (route, state) => {
  const auth = inject(Auth);
  return auth.hasToken() ? true : inject(Router).parseUrl('/login');
};
```

### Observable Streams

Async operations using RxJS:
```typescript
this.marketDataService.getInstruments()
  .pipe(
    catchError(err => this.notificationService.error(err)),
    finalize(() => this.loading.set(false))
  )
  .subscribe(data => this.instruments.set(data));
```

### Services for Business Logic

Angular services are singletons handling:
- API communication
- State management
- Cross-component communication

```typescript
@Injectable({ providedIn: 'root' })
export class OrderService {
  constructor(private http: HttpClient) { }
  placeOrder(request): Observable<OrderResponse> {
    return this.http.post('/api/orders', request);
  }
}
```

## Resilience Patterns

### JWT Token Management

- **Access Token** - Short-lived JWT in localStorage
- **Refresh Token** - Long-lived token in HTTP-only cookie
- **Auto-Refresh** - On 401 response, automatically refresh and retry request
- **Logout** - Clear token and redirect to login

### Idempotency

- Each mutation request gets unique `Idempotency-Key` header
- Server stores request → response mapping
- Retry with same key returns cached response
- Prevents duplicate order execution if network fails

### Error Handling

- Services catch HTTP errors and return user-friendly messages
- `NotificationService` displays toasts to user
- Components unsubscribe on destroy to prevent memory leaks

## Dependencies

**Runtime:**
- `@angular/core@22.1.0` - Core framework
- `@angular/common@22.1.0` - Common directives & pipes
- `@angular/forms@22.1.0` - Reactive forms
- `@angular/router@22.1.0` - Client-side routing
- `@angular/platform-browser@22.1.0` - Browser APIs
- `rxjs@7.8.0` - Reactive programming
- `angular-resizable-element@8.0.3` - Drag-to-resize panels
- `tslib@2.3.0` - TypeScript helpers

**Dev:**
- `@angular/cli@22.1.8` - Development tooling
- `typescript@6.0.2` - Type checking
- `vitest@4.0.8` - Unit test runner
- `jsdom@28.0.0` - DOM for tests
- `prettier@3.8.1` - Code formatting

## Integration with Backend

### Trading Platform

Frontend calls microservices:

**Trading Platform (port 8080):**
- Order placement & execution
- Account & position queries
- Order history

**Market Data Service (port 8081):**
- Instrument metadata
- Real-time market quotes

### OAuth/Auth Flow

1. User logs in at `/login`
2. Frontend sends credentials to `/auth/login`
3. Backend validates, returns `accessToken` & `refreshToken` (cookie)
4. Frontend stores accessToken in localStorage
5. JWT interceptor adds token to all requests
6. On 401, frontend calls `/auth/refresh` to get new token
7. Retry original request with new token

### Idempotency for Orders

All order mutations include `Idempotency-Key` header:
- Same key + same request = guaranteed same response
- Enables safe retry if network fails
- Prevents double-order if user clicks submit twice

## Performance Optimization

- **Lazy Loading** - Routes load modules on demand
- **OnPush Change Detection** - Components only update when inputs change
- **Signals** - More efficient than zone.js for frequent updates
- **Tree-Shaking** - Unused code removed from production build
- **Minification** - Production builds strip comments & shorten names

## Troubleshooting

### Login redirects to login after successful attempt

**Cause:** Token not stored properly  
**Solution:** Check localStorage in browser dev tools:
```javascript
localStorage.getItem('accessToken')  // Should have value
```

### 401 Unauthorized on API calls

**Cause:** Token expired or invalid  
**Solution:**
1. Check token in localStorage
2. Try refreshing page (may trigger token refresh)
3. Clear localStorage and login again

### Dashboard shows no data

**Cause:** Market data service not running  
**Solution:**
```bash
# In separate terminal
cd backend/market-data-service
mvn spring-boot:run
```

### Styles not loading

**Cause:** Global styles.scss not imported  
**Solution:** Check `angular.json` has `styles: ["src/styles.scss"]`

### ng serve fails to start

**Cause:** Port 4200 in use  
**Solution:**
```bash
# Use different port
ng serve --port 4300

# Or kill process using port
# Windows: netstat -ano | findstr :4200
# Mac/Linux: lsof -i :4200 | kill -9
```

## Testing Strategy

- **Unit Tests** - Services, components, guards with mocked HTTP
- **Component Tests** - Template rendering, user interactions
- **Integration Tests** - Multiple components working together
- **E2E Tests** - Full user workflows (future)

Run tests:
```bash
npm test                    # Run once
ng test --watch            # Watch mode
ng test --code-coverage    # With coverage report
```

## Project Standards

### Code Style

- **Formatting** - Prettier (2 spaces, single quotes)
- **Linting** - ESLint (via Angular CLI)
- **Type Safety** - Strict TypeScript (`strict: true`)

### File Naming

- Components: `*.component.ts` (e.g., `dashboard.component.ts`)
- Services: `.ts` (e.g., `order.ts`)
- Tests: `.spec.ts` (e.g., `order.spec.ts`)
- Directives: `.directive.ts` (e.g., `resizable.directive.ts`)

### Organization

- One component/service per file
- Related files in same folder
- Core utilities in `/core`
- Feature-specific code in `/features`

---

**Application Version:** 0.0.0  
**Angular Version:** 22.1.0  
**Node Version:** 18+  
**Last Updated:** October 2026  
**Maintainer:** GOATeen Trading Platform Team
