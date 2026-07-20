#!/bin/bash
#
# Start the demo's Postgres via Podman Compose. Idempotent: brings any
# existing instance down first so re-running this script doesn't pile up
# stale containers.
#
# Requires:
#   - podman 5+ (https://podman.io/)
#   - a compose provider on the PATH (podman ships `docker-compose` or
#     `podman-compose` as the backend; `podman compose` figures it out).
#
# Once Postgres is healthy, run the app:
#   mvn -f pom.xml spring-boot:run
#
# The app connects to localhost:5433 with the credentials from
# .env.docker (default user/password = sui / sui, db = sui_shop).

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f .env.docker ]; then
  echo "ERROR: .env.docker not found. Copy .env.docker.example and adjust if needed."
  exit 1
fi

podman compose --env-file .env.docker -f docker-compose.yml down 2>/dev/null || true
podman compose --env-file .env.docker -f docker-compose.yml up -d

echo ""
echo "Postgres starting on localhost:5433"
echo "  database: $(grep ^POSTGRES_DB= .env.docker     | cut -d= -f2)"
echo "  user:     $(grep ^POSTGRES_USER= .env.docker   | cut -d= -f2)"
echo ""
echo "Now run the app:"
echo "  mvn -f \"$SCRIPT_DIR/pom.xml\" spring-boot:run"
echo "Then open:"
echo "  http://localhost:8080/"
