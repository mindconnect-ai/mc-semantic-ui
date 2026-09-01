#!/bin/bash
#
# Stop the demo's Postgres. By default keeps the data volume so the next
# `start-postgres.sh` finds the seeded products. Pass `--clean` to drop the
# volume too (fresh DB on the next start, sample data re-seeded).

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ "$1" = "--clean" ]; then
  echo "Stopping Postgres AND dropping the postgres_data volume."
  podman compose --env-file .env.docker -f docker-compose.yml down -v
else
  echo "Stopping Postgres (data volume preserved)."
  echo "Use --clean to also drop the volume."
  podman compose --env-file .env.docker -f docker-compose.yml down
fi
