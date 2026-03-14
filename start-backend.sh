#!/usr/bin/env bash
# Start only the backend (Spring Boot). Use this in one terminal; run frontend in another.
# Usage: ./start-backend.sh
# Stop: Ctrl+C
# Requires: MySQL, Redis, etc. — run ./start-dependencies.sh first if needed.

set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

mysql_reachable() {
  if command -v nc &>/dev/null && nc -z 127.0.0.1 3306 2>/dev/null; then return 0; fi
  if python3 -c "import socket; s=socket.socket(); s.settimeout(2); s.connect(('127.0.0.1',3306)); s.close()" 2>/dev/null; then return 0; fi
  if python -c "import socket; s=socket.socket(); s.settimeout(2); s.connect(('127.0.0.1',3306)); s.close()" 2>/dev/null; then return 0; fi
  return 1
}
if ! mysql_reachable; then
  echo "ERROR: MySQL is not running on 127.0.0.1:3306."
  echo "Run first: ./start-dependencies.sh"
  echo "Or: docker-compose -f docker-compose/dependencies.yml up -d mysql redis cassandra rabbitmq-stomp minio"
  echo "Wait ~30-60s, then run ./start-backend.sh again."
  exit 1
fi

cd "$ROOT/ebook-chat"
echo "Starting backend at http://localhost:8080 (Ctrl+C to stop)..."
exec mvn spring-boot:run
