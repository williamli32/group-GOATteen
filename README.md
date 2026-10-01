# LEAP Trading Platform

## Project Overview

Group GOATteen trading platform, G.O.A.T. Platforms. Developed by Prasamsha Dahal, Niyati Goswami, William Li, Nowsin Mozemder, Seeyan Newaz, Hannah Ton.

Direct-to-consumer trading platform built using:

## Technology Stack

### Backend

#### Technologies Used
- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA

#### Organization

The backend is organized into two subprojects: the **Trading Platform** (port 8080) and a **Market Data Service** (port 8081). 

The trading platform is the core backend, handling order execution, account management, and trade settlement with additional features such as pessimistic locking and idempotency protection, working with the backend database to store information. 

The market data service is an auxiliary microservice that simulates real-time market quotes across multiple asset classes (US and international stocks, forex, crypto) with prices updating every 3 seconds using a random walk algorithm, providing the quote feeds that the trading platform uses when executing orders. 

### Frontend

#### Technologies Used
- Angular
- TypeScript

#### Organization

The frontend is organized through multiple sections: 
- **Core** module with authentication services, route guards, and HTTP interceptors
- **Features** module containing login/register components and account management
- **Dashboard** for displaying trading activity and account information. The application uses standalone components with Angular's latest routing patterns and RxJS observables for reactive state management
- Later components will be added as the frontend gets developed

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