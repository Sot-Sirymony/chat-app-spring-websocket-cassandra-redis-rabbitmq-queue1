#!/usr/bin/env bash
# Start only the backend (Spring Boot). Use this in one terminal; run frontend in another.
# Usage: ./start-backend.sh
# Stop: Ctrl+C

set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT/ebook-chat"
echo "Starting backend at http://localhost:8080 (Ctrl+C to stop)..."
exec mvn spring-boot:run
