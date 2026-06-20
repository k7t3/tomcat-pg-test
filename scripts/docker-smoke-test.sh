#!/bin/sh
set -eu

cleanup() {
    docker compose down -v >/dev/null 2>&1 || true
}

trap cleanup EXIT INT TERM

docker compose up --build -d

attempt=1
while [ "$attempt" -le 30 ]; do
    response=$(curl -fsS http://localhost:8080/health || true)
    if [ -n "$response" ]; then
        break
    fi
    attempt=$((attempt + 1))
    sleep 2
done

if [ -z "${response:-}" ]; then
    echo "Timed out waiting for Tomcat health endpoint" >&2
    docker compose logs app db >&2 || true
    exit 1
fi

printf '%s' "$response" | grep -q "Database connection: OK"
printf '%s' "$response" | grep -q "customers"
printf '%s' "$response" | grep -q "Apache Tomcat"

printf 'Smoke test passed. /health returned expected content.\n'
