# LEAP Trading Platform

Direct-to-consumer trading platform built using:

## Technology Stack

### Backend
- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA

### Frontend
- Angular
- TypeScript

# Database
Start PostgreSQL:
docker compose up -d

Stop PostgreSQL:
docker compose down

View logs:
docker compose logs postgres

Connect:
psql -U postgres -h localhost -d leap_trading

## API Documentation
Swagger UI:

http://localhost:8080/swagger-ui/index.html

OpenAPI JSON:

http://localhost:8080/v3/api-docs

## Project Structure
- backend - Spring Boot API
- frontend - Angular application
- database - Database migrations
- docs - Architecture and documentation