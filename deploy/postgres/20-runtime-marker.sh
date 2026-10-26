#!/bin/sh
set -eu
# Called by the official entrypoint only after initializing a fresh cluster.
printf 'PostgreSQL 17 / Alpine runtime\n' > "$PGDATA/.leap-alpine-v1"
