# Sprint 2

## Sprint Goal

Implement secure client identity and authentication while establishing the Angular application foundation for the LEAP trading platform.

**Sprint Goal Status: COMPLETE**

---

## User Stories

### 1. Client Registration

As a new client, I want to register for an account so that I can securely access the LEAP trading platform.

#### Acceptance Criteria

- [x] User can register with first name, last name, email, and password
- [x] Registration data is validated
- [x] Duplicate email registration is prevented
- [x] Passwords are never stored in plain text
- [x] Registration creates a User, Client, and Account
- [x] Registered users receive the CLIENT role
- [x] Registration is transactional
- [x] Errors are returned in a structured format

**Status: COMPLETE**

---

### 2. Client Login and Session Management

As a registered client, I want to securely log in and maintain an authenticated session so that I can use protected platform features.

#### Acceptance Criteria

- [x] Valid credentials allow login
- [x] Invalid credentials return a generic authentication error
- [x] Disabled users cannot authenticate
- [x] Access tokens are time-limited
- [x] Refresh sessions are time-limited and revocable
- [x] Refresh tokens are stored securely using an HttpOnly cookie
- [x] Refresh tokens are rotated when a session is refreshed
- [x] Concurrent protected API failures share a single refresh operation
- [x] Logout revokes the active refresh session
- [x] Logout clears the browser refresh-token cookie
- [x] Expired or revoked sessions require re-authentication

**Status: COMPLETE**

---

### 3. Client Account Isolation

As a client, I want my account and trading information protected so that another client cannot access or modify it.

#### Acceptance Criteria

- [x] The backend derives the client's account from the authenticated user
- [x] Client APIs do not rely on user-supplied account IDs for authorization
- [x] A client cannot view another client's account
- [x] A client cannot view another client's holdings
- [x] A client cannot view another client's order history
- [x] Client-facing account APIs use authenticated current-user endpoints
- [x] A client cannot place orders against another client's account
- [x] Unauthorized access returns the appropriate HTTP response

#### Protected Current-User Endpoints

- `GET /api/accounts/me`
- `GET /api/accounts/me/holdings`
- `GET /api/accounts/me/blotter`

**Status: COMPLETE**

---

### 4. Angular Application Foundation

As a client, I want a clear and responsive application interface so that I can securely access the trading platform.

#### Acceptance Criteria

- [x] Angular application is initialized
- [x] Application structure separates core authentication, guards, interceptors, services, and feature components
- [x] Registration page is implemented
- [x] Login page is implemented
- [x] Authenticated dashboard is implemented
- [x] Dashboard displays authenticated current-user account data
- [x] Dashboard displays current-user holdings
- [x] Dashboard displays current-user order history
- [x] Protected routes use an authentication guard
- [x] Authentication credentials are attached to protected API calls
- [x] Expired access tokens trigger refresh-token session handling
- [x] Loading and error states are handled
- [x] Logout is available from the authenticated application
- [x] Logout revokes the backend session and clears local authentication state
- [x] Expired authentication is handled
- [x] UI is responsive on desktop and tablet
- [x] Trading controls remain non-functional until Sprint 3 to preserve sprint scope

**Status: COMPLETE**

---

## Technical Deliverables

- [x] Authentication/session database migration
- [x] `auth_sessions` persistence
- [x] User-role JPA relationship
- [x] Password hashing using BCrypt
- [x] Spring Security configuration
- [x] Registration API
- [x] Login API
- [x] JWT access authentication
- [x] Time-limited JWT access tokens
- [x] Refresh-token session management
- [x] Refresh-token rotation
- [x] Refresh-token hashing before database storage
- [x] HttpOnly refresh-token cookie
- [x] Logout and session revocation
- [x] Authenticated account resolution
- [x] Protected current-user account APIs
- [x] Angular project structure
- [x] Angular registration interface
- [x] Angular login interface
- [x] Angular authenticated dashboard
- [x] Current-user account service
- [x] Dashboard-to-backend integration
- [x] HTTP authentication interceptor
- [x] Single-flight access-token refresh handling
- [x] Route authentication guard
- [x] Login/logout navigation
- [x] Backend security and integration tests
- [x] Frontend unit tests
- [x] Angular production build verification
- [x] Docker-based Node.js frontend CI environment
- [x] Jenkins frontend test/build integration
- [x] Jenkins backend test/build integration
- [x] Jenkins PostgreSQL CI database
- [x] Full Jenkins CI verification

---

## Testing and CI Results

### Frontend

Angular/Vitest test suite completed successfully:

- **7 test files passed**
- **12 tests passed**
- Account service API tests passed
- Authentication guard tests passed
- Registration tests passed
- Login tests passed
- Application tests passed
- Authentication service tests passed
- JWT interceptor tests passed

Angular production build completed successfully.

### Backend

Spring Boot/Maven verification completed successfully using the Jenkins CI PostgreSQL environment.

### Jenkins

The Jenkins pipeline now verifies:

1. Source checkout
2. Angular dependency installation
3. Frontend unit tests
4. Angular production build
5. PostgreSQL CI database startup
6. Backend build and tests
7. CI database cleanup

Frontend Node.js execution is isolated using:

`node:24-bookworm`

The complete Jenkins pipeline completed successfully.

---

## Definition of Done

Sprint 2 is complete when a new client can:

1. [x] Register through the Angular application
2. [x] Log in securely
3. [x] Enter the authenticated application
4. [x] View their own account
5. [x] View their own holdings and order history
6. [x] Be prevented from accessing another client's data
7. [x] Maintain and refresh a valid session
8. [x] Log out and revoke the session
9. [x] Be redirected away from protected routes when unauthenticated
10. [x] Recover from an expired access token using the refresh session

Additional completion requirements:

- [x] Backend tests pass
- [x] Frontend tests pass
- [x] Angular production build succeeds
- [x] Jenkins CI pipeline completes successfully
- [x] Sprint 2 functionality is committed to `feature/sprint2-auth-ui`

---

## Sprint Outcome

Sprint 2 successfully established the secure client authentication and account-access foundation for LEAP.

Clients can now register, authenticate, maintain revocable sessions, access a protected Angular dashboard, view only their own account information, holdings, and order history, and securely log out.

The frontend and backend are integrated through authenticated current-user APIs and verified through automated frontend tests, backend tests, production builds, and Jenkins CI.

Market data, order-entry integration, order validation, and trading execution remain intentionally outside Sprint 2 and will be implemented beginning in Sprint 3.

---

## Status

**COMPLETE**