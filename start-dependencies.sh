#!/usr/bin/env bash
# Start MySQL, Redis, Cassandra, RabbitMQ, MinIO (required before backend).
# Usage: ./start-dependencies.sh
# Wait ~30-60s for healthy, then run ./run-dev.sh or ./start-backend.sh

set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
echo "Starting dependencies (mysql redis cassandra rabbitmq-stomp minio)..."
exec docker-compose -f docker-compose/dependencies.yml up -d mysql redis cassandra rabbitmq-stomp minio
