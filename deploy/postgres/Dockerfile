FROM postgres:17

# Build contexts are transferred by the Docker client. No host file mount is
# needed when a Windows client builds against a Linux Docker daemon.
COPY 10-app-user.sql /docker-entrypoint-initdb.d/10-app-user.sql
