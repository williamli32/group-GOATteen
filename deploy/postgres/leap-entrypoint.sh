#!/bin/sh
set -eu
# Match the official entrypoint's shorthand for PostgreSQL server flags.
case "${1:-}" in
    -*) set -- postgres "$@" ;;
esac
if [ "${1:-}" = postgres ] && [ -s "$PGDATA/PG_VERSION" ] && [ ! -f "$PGDATA/.leap-alpine-v1" ]; then
    echo "Existing PostgreSQL volume requires dump/restore before using the Alpine image." >&2
    echo "Run python deploy/migrate-postgres-runtime.py using the previous running database." >&2
    echo "The existing data has not been modified." >&2
    exit 1
fi
exec /usr/local/bin/docker-entrypoint.sh "$@"
