# Sprint 2

## Sprint Goal

Implement secure client identity and authentication while establishing the Angular application foundation for the LEAP trading platform.

## User Stories

### 1. Client Registration

As a new client, I want to register for an account so that I can securely access the LEAP trading platform.

#### Acceptance Criteria

- User can register with first name, last name, email, and password
- Registration data is validated
- Duplicate email registration is prevented
- Passwords are never stored in plain text
- Registration creates a User, Client, and Account
- Registered users receive the CLIENT role
- Registration is transactional
- Errors are returned in a structured format

### 2. Client Login and Session Management

As a registered client, I want to securely log in and maintain an authenticated session so that I can use protected platform features.

#### Acceptance Criteria

- Valid credentials allow login
- Invalid credentials return a generic authentication error
- Disabled users cannot authenticate
- Access tokens are time-limited
- Refresh sessions are time-limited and revocable
- Logout revokes the active refresh session
- Expired sessions require re-authentication

### 3. Client Account Isolation

As a client, I want my account and trading information protected so that another client cannot access or modify it.

#### Acceptance Criteria

- The backend derives the client's account from the authenticated user
- Client APIs do not rely on user-supplied account IDs for authorization
- A client cannot view another client's account
- A client cannot view another client's holdings
- A client cannot view another client's order history
- A client cannot place orders against another client's account
- Unauthorized access returns the appropriate HTTP response

### 4. Angular Application Foundation

As a client, I want a clear and responsive application interface so that I can securely access the trading platform.

#### Acceptance Criteria

- Angular application is initialized
- Application has a reusable authenticated layout
- Registration page is implemented
- Login page is implemented
- Authenticated dashboard shell is implemented
- Protected routes use an authentication guard
- Authentication credentials are attached to protected API calls
- Loading and error states are handled
- Logout is available
- Expired authentication is handled
- UI is responsive on desktop and tablet

## Technical Deliverables

- Authentication/session database migration
- User-role JPA relationship
- Password hashing
- Spring Security configuration
- Registration API
- Login API
- JWT access authentication
- Refresh-token session management
- Logout/revocation
- Authenticated account resolution
- Protected account APIs
- Angular project structure
- Angular registration and login interfaces
- Angular authenticated application shell
- HTTP interceptor
- Route guard
- Backend security and integration tests
- Frontend tests
- Jenkins CI verification

## Definition of Done

Sprint 2 is complete when a new client can:

1. Register through the Angular application
2. Log in securely
3. Enter the authenticated application
4. View their own account
5. Be prevented from accessing another client's data
6. Maintain and refresh a valid session
7. Log out and revoke the session

All backend and frontend tests must pass and the Jenkins CI pipeline must complete successfully.

## Status

In Progress