#!/usr/bin/env bash
# Run backend (Spring Boot) and frontend (Next.js) with auto-reload for development.
# Usage: ./run-dev.sh
# Stop: Ctrl+C (stops both).
# Requires: MySQL, Redis, Cassandra, RabbitMQ, MinIO — start with:
#   docker-compose -f docker-compose/dependencies.yml up -d mysql redis cassandra rabbitmq-stomp minio

set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# Fail fast if MySQL is not reachable (avoids "Communications link failure" after 20s)
mysql_reachable() {
  if command -v nc &>/dev/null && nc -z 127.0.0.1 3306 2>/dev/null; then return 0; fi
  if python3 -c "import socket; s=socket.socket(); s.settimeout(2); s.connect(('127.0.0.1',3306)); s.close()" 2>/dev/null; then return 0; fi
  if python -c "import socket; s=socket.socket(); s.settimeout(2); s.connect(('127.0.0.1',3306)); s.close()" 2>/dev/null; then return 0; fi
  return 1
}
if ! mysql_reachable; then
  echo "ERROR: MySQL is not running on 127.0.0.1:3306."
  echo "Start dependencies first: ./start-dependencies.sh"
  echo "Or: docker-compose -f docker-compose/dependencies.yml up -d mysql redis cassandra rabbitmq-stomp minio"
  echo "Wait until healthy (~30–60s), then run ./run-dev.sh again."
  exit 1
fi

BACKEND_PID=""
cleanup() {
  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "Stopping backend (PID $BACKEND_PID)..."
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  exit 0
}
trap cleanup SIGINT SIGTERM

echo "Starting backend (Spring Boot) in background..."
(cd ebook-chat && exec mvn spring-boot:run) &
BACKEND_PID=$!

echo "Waiting for backend to be ready (up to 90s)..."
BACKEND_UP=""
for i in $(seq 1 90); do
  if curl -s -o /dev/null --connect-timeout 2 http://localhost:8080/ 2>/dev/null; then
    BACKEND_UP=1
    echo "Backend is up at http://localhost:8080"
    break
  fi
  sleep 1
done
if [[ -z "$BACKEND_UP" ]]; then
  echo "WARNING: Backend did not respond after 90s. Start dependencies first: docker-compose -f docker-compose/dependencies.yml up -d mysql redis cassandra rabbitmq-stomp minio"
  echo "Then check the backend terminal for errors. You can still use the app; try download again once backend is up."
fi

echo "Starting frontend (Next.js) in foreground..."
cd frontend && npm run dev
