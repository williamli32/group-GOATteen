# Full-stack container deployment

**Existing deployments:** apply the [security runtime migration](security-remediation.md) before rebuilding the PostgreSQL image. The image now uses Alpine; existing Debian clusters require a verified dump/restore into a new volume.

Baseline: `feature/more-graphs`, commit `dc32f91380fb43d8dea4ddcac21dd33700db3173`.

This setup runs the application on one Linux Docker host, including an EC2 VM.
Use `compose.deploy.yaml` explicitly. The existing `docker-compose.yml` remains
the database-only development setup.

| Container | Responsibility | Host ports |
| --- | --- | --- |
| frontend | Built Angular app, SPA routing, API reverse proxy and optional HTTPS | 8082/8443 for a local trial; 80/443 for a public hostname |
| trading-platform | Java 21 trading API, Flyway, reporting, recovery, alerts | None |
| market-data-service | Existing simulated market quotes | None |
| postgres | PostgreSQL 17, dedicated application database and login | None |

The production Angular build already uses `/api`. Caddy forwards that prefix to
`trading-platform:8080` without removing it. Trading requests simulated quotes
from `market-data-service:8081`. Containers use Docker DNS, not the Windows SSH
database tunnel. The browser only needs the frontend URL.

## 1. Prepare the Linux host

Install Docker Engine and the [Docker Compose plugin](https://docs.docker.com/compose/install/linux/).
Use a current Compose release with `up --wait` support. Commands below use
`docker compose`; substitute `docker-compose` if your installation provides the
standalone Compose binary. Python 3 is used by the
credential generator and the optional read-only smoke check. No host Java,
Maven, Node, Angular CLI or PostgreSQL installation is needed.

Allow enough memory for two JVMs, PostgreSQL and the frontend. A host with at
least 4 GiB RAM is a practical starting point; image builds can require more.
The two application JVMs have container memory limits, and Docker logs rotate.
A small deployment host can pull images built on another machine instead.

Check that Docker is available to your Linux user:

```bash
docker version
docker compose version
```

For a new installation, from the repository root:

```bash
git switch feature/sprint-8-goal1
git pull --ff-only
python deploy/init-env.py
docker-compose --env-file .env.deploy -f compose.deploy.yaml config --quiet
docker-compose --env-file .env.deploy -f compose.deploy.yaml up -d --build --wait --wait-timeout 240
docker-compose --env-file .env.deploy -f compose.deploy.yaml ps
python deploy/smoke-check.py http://localhost:8082
```

On Linux, use `python3` if the `python` command is unavailable.

The generator creates `.env.deploy` with a distinct database password,
PostgreSQL administrator password, a Base64 JWT secret and a shared
analyst/operations signup code. It refuses to overwrite an existing file.
Keep that file across builds and restarts. On Linux its permissions are 0600;
on Windows store it in a directory accessible only to the deployment operator.
Do not commit it. To configure the values yourself, copy `.env.deploy.example`
and fill all four blank secrets; the JWT secret must decode to at least 32 bytes.

The app connects as `leap_app`, not as the PostgreSQL superuser. The database
initialization file creates that login and gives it ownership of this database.
`deploy/postgres/Dockerfile` copies the initialization SQL into a small image
based on the official PostgreSQL 17 image. No repository file is bind-mounted
at runtime, so Windows builds do not pass a Windows filesystem path to the
Linux Docker daemon. New installations use a named volume; the runtime migration selects a new verified volume for existing installations.
Initialization runs only on an empty PostgreSQL volume. Editing the password
in `.env.deploy` later does not change an existing database role's password.

Use the generated `DEPLOY_INTERNAL_ACCESS_CODE` when signing up as Analyst or
Operations. The previous development access code remains the default only for
the existing local Spring profile. All three roles and USD/GBP/INR stay available.

## 2. Use the Linux-hosted app from Windows

The default web binding is Linux loopback. This lets you try the complete stack
alongside the existing `leap-postgres` container without opening a web port.

Open a PowerShell terminal on Windows:

```powershell
ssh -N -L 8082:127.0.0.1:8082 ec2-user@<linux-ip>
```

Leave that terminal open and visit `http://localhost:8082` in Windows.
Your three local `spring-boot:run`/`ng serve` processes and the 5433 database
tunnel are not needed by this stack. They can remain available for separate
development work.

Alternatively, run the same Compose command with Docker Desktop in Linux
container mode on Windows. Use `python deploy/init-env.py` if your Windows
Python command is `python`, then visit `http://localhost:8082` directly.

## 3. Enable a cloud hostname and HTTPS

Choose your actual hostname and point its public DNS A/AAAA records at this
Docker host. Edit these entries in `.env.deploy`:

```dotenv
APP_BIND_ADDRESS=0.0.0.0
APP_HTTP_PORT=80
APP_HTTPS_PORT=443
APP_SITE_ADDRESS=trading.example.com
APP_CORS_ALLOWED_ORIGINS=https://trading.example.com
```

Replace `trading.example.com` with your real hostname. Allow inbound TCP 80
and 443 in the cloud security group and host firewall, then run:

```bash
docker compose --env-file .env.deploy -f compose.deploy.yaml up -d --wait --wait-timeout 240
python3 deploy/smoke-check.py https://trading.example.com
```

Caddy obtains and renews the certificate and redirects HTTP to HTTPS. Its
certificate/configuration directories are persistent volumes. The current
`10.14.137.224` address is private; it does not itself provide a publicly
reachable domain. For an internal-only trial, keep the SSH tunnel above.

TLS terminates at Caddy. It replaces forwarding headers before proxying;
the container Spring profile uses forwarded request information. Refresh,
rotation and logout cookies therefore use Secure on HTTPS, HttpOnly and
SameSite=Lax, while ordinary HTTP localhost development continues to work.
Do not publish the trading or market service ports around this proxy.

If you add a separate load balancer/TLS terminator later, configure its trusted
proxy chain deliberately; this Compose setup terminates public TLS at Caddy.

## Database lifetime and existing data

This creates a **new** database in `leap-app_app_postgres_data`, separate from
the existing `leap-postgres` container and its dev/test databases. It does not
copy existing users, account balances, trades, alerts or reporting records.
Flyway initializes and migrates the deployment database on application startup.
The container profile validates migration checksums and JPA mappings.

You can leave the old container running. No deployment service publishes 5432,
8080 or 8081, and the deployment does not create a test database.

To migrate existing development records, do this on **Linux**, before the first
start of trading-platform. Keep a backup of the source database. These commands
restore into a new, empty deployment database:

```bash
mkdir -p deploy/backups
chmod 700 deploy/backups
docker exec leap-postgres pg_dump -U postgres -d leap_trading -Fc --no-owner --no-privileges > deploy/backups/pre-container.dump

docker compose --env-file .env.deploy -f compose.deploy.yaml up -d --wait postgres
docker compose --env-file .env.deploy -f compose.deploy.yaml exec -T postgres sh -c 'exec pg_restore --exit-on-error --no-owner --no-privileges -U "$APP_DB_USERNAME" -d "$POSTGRES_DB"' < deploy/backups/pre-container.dump

docker compose --env-file .env.deploy -f compose.deploy.yaml up -d --build --wait --wait-timeout 240
```

Do not restore that dump into a database already initialized by the trading app.
If migration checksum validation reports older edited migrations after a restore,
resolve the migration history before proceeding; the deployment does not silently
repair it. The source dev/test databases are never altered by these commands.

Changing or rebuilding app images retains named volumes. Ordinary shutdown:

```bash
docker compose --env-file .env.deploy -f compose.deploy.yaml down
```

`down -v` deletes the deployment database and certificate volumes; it is a reset,
not a normal stop. Do not use it for an upgrade.

Back up the running deployment database on Linux:

```bash
mkdir -p deploy/backups
chmod 700 deploy/backups
docker compose --env-file .env.deploy -f compose.deploy.yaml exec -T postgres sh -c 'exec pg_dump -U "$APP_DB_USERNAME" -d "$POSTGRES_DB" -Fc --no-owner --no-privileges' > deploy/backups/leap-trading.dump
```

Keep backups outside the VM as well. The market simulator still initializes its
in-memory prices from its CSV on restart; persisted trading records remain in
PostgreSQL. Containerizing it does not turn it into an external live-market feed.

## Updates and registry images

For an update built on this host:

```bash
git pull --ff-only
docker compose --env-file .env.deploy -f compose.deploy.yaml up -d --build --wait --wait-timeout 240
python3 deploy/smoke-check.py http://localhost:8082
```

Use the HTTPS URL instead if configured. Back up the database before a release
that changes migrations. Check logs if `--wait` reports an unhealthy service.

To build elsewhere and pull on the VM, set the same image prefix and a specific
release tag in each machine's `.env.deploy`, for example:

```dotenv
LEAP_IMAGE_PREFIX=ghcr.io/williamli32/group-goatteen
LEAP_IMAGE_TAG=release-2026-10-23
```

After authenticating to your registry, the build machine can run:

```bash
docker compose --env-file .env.deploy -f compose.deploy.yaml build --pull
docker compose --env-file .env.deploy -f compose.deploy.yaml push postgres trading-platform market-data-service frontend
```

The deployment host can then run:

```bash
docker compose --env-file .env.deploy -f compose.deploy.yaml pull
docker compose --env-file .env.deploy -f compose.deploy.yaml up -d --no-build --wait --wait-timeout 240
```

The three application images and the custom PostgreSQL image are built/pushed.
The PostgreSQL image contains the initialization SQL but no credentials. For a
pull-only deployment, the Compose file and `.env.deploy` must be present on the
deployment host; the SQL file is already inside the image. A machine building
the images needs the source checkout, including `deploy/postgres`. Keep the
deployment environment file on that host; it is not part of any image. The existing Jenkins pipeline remains
the test pipeline and does not publish images or deploy automatically.

## Windows SQL-file mount error

If startup previously failed with `invalid volume specification` and a path
such as `C:\Users\...\deploy\postgres\10-app-user.sql`, update to the custom
PostgreSQL image configuration above and rebuild:

```powershell
docker-compose --env-file .env.deploy -f compose.deploy.yaml up -d --build --wait --wait-timeout 240
docker-compose --env-file .env.deploy -f compose.deploy.yaml ps
```

Keep the existing `.env.deploy` and named volumes. The initialization script
still runs only for an empty database volume. Updating the image does not
reinitialize an existing database.

When your Docker daemon is on the Linux server, published loopback ports are
on that server. Keep the frontend SSH tunnel from section 2 open on Windows
before visiting localhost or running `deploy/smoke-check.py`. Docker Desktop
running locally does not require that SSH tunnel.

## Troubleshooting and verification

```bash
docker compose --env-file .env.deploy -f compose.deploy.yaml logs --tail 100 trading-platform
docker compose --env-file .env.deploy -f compose.deploy.yaml logs --tail 100 market-data-service
docker compose --env-file .env.deploy -f compose.deploy.yaml logs --tail 100 frontend
docker compose --env-file .env.deploy -f compose.deploy.yaml exec postgres sh -c 'exec psql -U "$APP_DB_USERNAME" -d "$POSTGRES_DB"'
```

The database must become healthy before trading starts, the market service
must be ready before trading starts, and trading must be ready before the
frontend starts. Trading readiness includes an actual database health check.
Actuator is not proxied publicly. Restart policies recover exited containers;
a failed health check reports unhealthy status and does not alone restart a
running process.

The smoke check is read-only. It verifies frontend deep links, API routing,
the three signup currencies, authentication, private management routes and
missing asset handling. It does not prove certificate issuance, every persona
journey, disaster recovery or load capacity. Run the existing frontend/backend
regression suites as release checks; image builds intentionally skip tests
because they do not have a test database.

Useful upstream references:

- [Compose startup readiness](https://docs.docker.com/compose/how-tos/startup-order/)
- [Caddy HTTPS setup](https://caddyserver.com/docs/quick-starts/https)
- [Spring behind a proxy](https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server)
- [Spring Boot container images](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html)

## Validation of this change

- Production Angular build succeeds; the existing component stylesheet budget
  warnings remain.
- Both backend executable JARs build with Java 21.
- 95 focused backend tests pass, including the existing role security tests,
  configurable staff registration, HTTP/HTTPS cookie handling and proxy headers.
  The restricted test workspace uses a Mockito startup agent for this run.
- The packaged market service starts with the container profile, reports readiness
  UP and serves advancing quotes from its packaged CSV.
- Docker Compose validates both local and public-hostname configurations and
  refuses the blank example secrets.
- Caddy validates the configuration. HTTP checks with the real built frontend
  and a fixture API cover SPA routes, API forwarding, cache headers, private
  management endpoints, missing assets and forwarding-header sanitization.

Docker Engine is unavailable in the implementation workspace, so image builds
and the combined four-container startup have not been executed there. The
`up --build --wait` and smoke-check commands above are the final checks to run
on your Docker host. Public certificate issuance also needs your actual DNS
and network configuration.
