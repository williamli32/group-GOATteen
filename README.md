# LEAP Trading Platform

## Project Overview

Group GOATteen trading platform, G.O.A.T. Platforms.

Developed by Prasamsha Dahal, Niyati Goswami, William Li, Nowsin Mozemder, Seeyan Newaz, Hannah Ton.

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

The Trading Platform is the core backend, handling order execution, account management, and trade settlement with additional features such as pessimistic locking and idempotency protection, working with the backend database to store information.

The Market Data Service is an auxiliary microservice that simulates real-time market quotes across multiple asset classes (US and international stocks, forex, crypto) with prices updating every 3 seconds using a random walk algorithm, providing the quote feeds that the Trading Platform uses when executing orders.

### Frontend

#### Technologies Used

- Angular
- TypeScript

#### Organization

The frontend is organized through multiple sections:

- **Core** module with authentication services, route guards, and HTTP interceptors
- **Features** module containing login/register components and account management
- **Dashboard** for displaying trading activity and account information
- The application uses standalone components with Angular routing patterns and RxJS observables for reactive state management
- Later components will be added as the frontend gets developed

---

# Development Environment Setup

The development environment uses a Windows machine for development and a remote Linux/EC2 machine for Docker.

Docker and PostgreSQL run on the Linux server, while the Spring Boot Trading Platform runs locally on Windows.

The Windows machine communicates with:

1. The remote Docker daemon through SSH using `DOCKER_HOST`
2. PostgreSQL through an SSH tunnel from Windows port `5433` to Linux port `5432`

The database connection route is:

```text
Windows Development Machine
        |
        | Spring Boot
        |
        | jdbc:postgresql://localhost:5433/leap_trading
        |
        v
Windows localhost:5433
        |
        | SSH Tunnel
        v
Linux / EC2 localhost:5432
        |
        v
Docker
        |
        v
leap-postgres
        |
        v
PostgreSQL :5432
        |
        v
leap_trading
```

---

# One-Time Windows Setup

The following steps only need to be completed once on a new Windows development machine.

## 1. Install Docker CLI

Open PowerShell from your Windows home directory.

Run:

```powershell
winget install Docker.DockerCLI
```

## 2. Install Docker Compose

Run:

```powershell
winget install Docker.DockerCompose
```

## 3. Configure the Remote Docker Host

Set the `DOCKER_HOST` Windows user environment variable:

```powershell
[System.Environment]::SetEnvironmentVariable("DOCKER_HOST", "ssh://ec2-user@<your_linux_ip>", "User")
```

Replace:

```text
<your_linux_ip>
```

with the IP address of the Linux/EC2 machine.

Example:

```powershell
[System.Environment]::SetEnvironmentVariable("DOCKER_HOST", "ssh://ec2-user@10.14.137.224", "User")
```

After running this command, close and reopen PowerShell or VS Code so the new environment variable is loaded.

Verify it:

```powershell
echo $env:DOCKER_HOST
```

Expected format:

```text
ssh://ec2-user@<your_linux_ip>
```

You can verify which Docker host is being used with:

```powershell
docker info --format '{{.Name}}'
```

The returned name should identify the remote Linux/EC2 machine.

You can also check:

```powershell
docker context ls
```

> **Important:** Because `DOCKER_HOST` points to the Linux server, commands such as `docker ps`, `docker-compose up`, `docker-compose down`, `docker exec`, and `docker logs` operate on Docker running on the Linux machine, not on local Windows Docker.

---

# SSH Key Setup

Generate an SSH key from PowerShell:

```powershell
ssh-keygen
```

Press **Enter** for all prompts unless you specifically want to use a custom key location or passphrase.

The default public key is usually:

```text
C:\Users\<your_username>\.ssh\id_ed25519.pub
```

Copy the SSH public key to the Linux server:

```powershell
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh ec2-user@<your_linux_ip> "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
```

Example:

```powershell
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh ec2-user@10.14.137.224 "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
```

Verify that SSH works:

```powershell
ssh ec2-user@<your_linux_ip>
```

If the connection succeeds, exit the Linux shell:

```bash
exit
```

---

# SSH Tunnel Setup

The Trading Platform runs locally on Windows while PostgreSQL runs inside Docker on Linux.

An SSH tunnel is therefore required so that Spring Boot can access PostgreSQL securely.

The tunnel forwards:

```text
Windows localhost:5433
        |
        | SSH
        v
Linux localhost:5432
        |
        v
Docker PostgreSQL
```

## Start the SSH Tunnel

Open **PowerShell inside VS Code**.

From the project root, run:

```powershell
ssh -L 5433:localhost:5432 ec2-user@<your_linux_ip>
```

Example:

```powershell
ssh -L 5433:localhost:5432 ec2-user@10.14.137.224
```

Keep this terminal open at all times while developing.

Closing this terminal closes the SSH tunnel and the locally running Spring Boot application will no longer be able to access PostgreSQL.

### Optional Tunnel-Only Command

To create the tunnel without opening an interactive Linux shell, use:

```powershell
ssh -N -L 5433:localhost:5432 ec2-user@<your_linux_ip>
```

Keep this terminal open while working.

---

# Database

PostgreSQL runs inside Docker on the remote Linux machine.

## Start PostgreSQL

Open another **PowerShell terminal inside VS Code**.

From the project root:

```powershell
docker-compose up -d
```

Verify that PostgreSQL is running:

```powershell
docker ps
```

The PostgreSQL container should appear as:

```text
leap-postgres
```

## View PostgreSQL Logs

```powershell
docker-compose logs postgres
```

To continuously follow the logs:

```powershell
docker-compose logs -f postgres
```

## Stop PostgreSQL

```powershell
docker-compose down
```

The PostgreSQL data remains stored in the Docker volume.

Do not use:

```powershell
docker-compose down -v
```

unless you intentionally want to delete the PostgreSQL volume and its stored data.

---

# Development Database Setup

The main development database is:

```text
leap_trading
```

## Check Whether `leap_trading` Exists

Connect to the default PostgreSQL database:

```powershell
docker exec -it leap-postgres psql -U postgres -d postgres
```

Inside PostgreSQL, list the databases:

```sql
\l
```

Look for:

```text
leap_trading
```

Exit PostgreSQL with:

```sql
\q
```

---

## Create `leap_trading` If Required

If `leap_trading` does not exist, connect to PostgreSQL:

```powershell
docker exec -it leap-postgres psql -U postgres -d postgres
```

Create the development database:

```sql
CREATE DATABASE leap_trading;
```

Verify that it exists:

```sql
\l
```

Exit PostgreSQL:

```sql
\q
```

When the Trading Platform backend starts, Flyway automatically applies the required database migrations.

---

# Connect to the Development Database

To connect directly to PostgreSQL inside the Docker container:

```powershell
docker exec -it leap-postgres psql -U postgres -d leap_trading
```

# Connecting Through the SSH Tunnel

With the SSH tunnel running, PostgreSQL can also be accessed from Windows through port `5433`.

If `psql` is available locally:

```powershell
psql -h localhost -p 5433 -U postgres -d leap_trading
```

This accesses the same database as:

```powershell
docker exec -it leap-postgres psql -U postgres -d leap_trading
```

The difference is the route used.

Direct Docker connection:

```text
Windows Docker CLI
        |
        | SSH through DOCKER_HOST
        v
Linux Docker daemon
        |
        v
leap-postgres
        |
        v
PostgreSQL
```

Spring Boot / SSH tunnel connection:

```text
Windows
        |
        v
localhost:5433
        |
        | SSH Tunnel
        v
Linux localhost:5432
        |
        v
leap-postgres
        |
        v
PostgreSQL
```

Both routes access the same PostgreSQL instance.

---

# Spring Boot Development Configuration

The Trading Platform backend connects to PostgreSQL through the SSH tunnel.

The development datasource should therefore use:

```text
jdbc:postgresql://localhost:5433/leap_trading
```

Example `application-dev.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/leap_trading
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

Database passwords should not be committed to source control.

Example using PowerShell:

```powershell
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your-database-password"
```

Example using Git Bash:

```bash
export DB_USERNAME=postgres
export DB_PASSWORD='your-database-password'
```

---

# Daily Development Startup

The recommended startup process uses multiple VS Code terminals.

## Terminal 1 - PowerShell - SSH Tunnel

Open PowerShell inside VS Code.

From the project root:

```powershell
ssh -L 5433:localhost:5432 ec2-user@<your_linux_ip>
```

Example:

```powershell
ssh -L 5433:localhost:5432 ec2-user@10.14.137.224
```

Keep this terminal open at all times.

---

## Terminal 2 - PowerShell - Docker

Open another PowerShell terminal inside VS Code.

From the project root:

```powershell
docker-compose up -d
```

Verify Docker services:

```powershell
docker ps
```

Connect to the database if required:

```powershell
docker exec -it leap-postgres psql -U postgres -d leap_trading
```

---

## Terminal 3 - Git Bash - Trading Platform Backend

Open Git Bash inside VS Code.

From the project root:

```bash
cd backend/trading-platform
```

Start Spring Boot:

```bash
./mvnw spring-boot:run
```

If the `dev` profile needs to be explicitly enabled:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The Trading Platform backend runs on:

```text
http://localhost:8080
```

---

## Terminal 4 - Market Data Service

Open another terminal.

From the project root:

```bash
cd backend/market-data-service
```

Start the Market Data Service:

```bash
./mvnw spring-boot:run
```

The Market Data Service runs on:

```text
http://localhost:8081
```

---

## Terminal 5 - Angular Frontend

Open another terminal.

From the project root:

```bash
cd frontend
```

Install frontend dependencies if required:

```bash
npm install
```

Start Angular:

```bash
npm start
```

or:

```bash
ng serve
```

The Angular frontend runs on:

```text
http://localhost:4200
```

---

# Normal Startup Summary

For normal development, use the following order:

### 1. Open PowerShell and start the SSH tunnel

```powershell
ssh -L 5433:localhost:5432 ec2-user@<your_linux_ip>
```

Keep this terminal open.

### 2. Open another PowerShell terminal and start Docker

```powershell
docker-compose up -d
```

### 3. Verify PostgreSQL

```powershell
docker ps
```

### 4. If required, connect to PostgreSQL

```powershell
docker exec -it leap-postgres psql -U postgres -d leap_trading
```

### 5. Open Git Bash and start the Trading Platform

```bash
cd backend/trading-platform
./mvnw spring-boot:run
```

### 6. Start the Market Data Service

```bash
cd backend/market-data-service
./mvnw spring-boot:run
```

### 7. Start the Angular frontend

```bash
cd frontend
npm start
```

---

# Application Ports

| Application | Port |
|---|---:|
| Angular Frontend | 4200 |
| Trading Platform Backend | 8080 |
| Market Data Service | 8081 |
| Windows PostgreSQL SSH Tunnel | 5433 |
| Linux/Docker PostgreSQL | 5432 |

---

# API Documentation

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

# Project Structure

```text
group-GOATteen/
|
|-- backend/
|   |
|   |-- trading-platform/
|   |   `-- Core Spring Boot trading application
|   |
|   `-- market-data-service/
|       `-- Simulated market quote service
|
|-- frontend/
|   `-- Angular client application
|
|-- database/
|   `-- Database-related resources
|
|-- docs/
|   `-- Architecture and project documentation
|
|-- docker-compose.yaml
|
`-- README.md
```

## Main Directories

- `backend` - Spring Boot backend services
- `backend/trading-platform` - Order management, account management, execution, settlement, audit, recovery and API functionality
- `backend/market-data-service` - Simulated market quotes
- `frontend` - Angular application
- `database` - Database-related resources
- `docs` - Architecture and project documentation