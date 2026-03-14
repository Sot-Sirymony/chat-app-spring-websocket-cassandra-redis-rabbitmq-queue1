# Quick start (avoid "Communications link failure")

**1. Start dependencies (required before backend):**
```bash
./start-dependencies.sh
```
Or: `docker-compose -f docker-compose/dependencies.yml up -d mysql redis cassandra rabbitmq-stomp minio`  
Wait until containers are healthy (~30–60s). Check: `docker-compose -f docker-compose/dependencies.yml ps`

**2. Run app:**
```bash
./run-dev.sh
```
If you see `ERROR: MySQL is not running on 127.0.0.1:3306`, run step 1 first, wait, then run step 2 again.  
Make scripts executable once: `chmod +x run-dev.sh start-dependencies.sh start-backend.sh`

---

# Credential Test (3 users for manual testing: 1 admin, 2 non-admin)
| Role   | Username     | Password   |
|--------|--------------|------------|
| Admin  | admin        | admin      |
| User 1 | sirymony.sot | Xmk2pJUj   |
| User 2 | dalen.phea   | IAhSfRn9   |

Use these in **docs/File-And-Message-Test-Cases.md** (e.g. D1–D3: who can download WARN vs gated files).

---

# Docker Compose Down

 docker-compose -f docker-compose/dependencies.yml down
 docker-compose -f docker-compose/presidio.yml down
 
 cd "/Users/sotsirymony/Desktop/Chat System asesstment II/chat-app-spring-websocket-cassandra-redis-rabbitmq"
docker-compose -f docker-compose/dependencies.yml down



# Docker Compose UP
docker-compose -f docker-compose/dependencies.yml up

docker-compose -f docker-compose/dependencies.yml up -d mysql redis cassandra rabbitmq-stomp minio

docker compose -f docker-compose/dependencies.yml up -d

# Auto-reload while developing (no manual restart)

**Frontend:** Run `npm run dev` in `frontend/`. Next.js hot-reloads on save.

**Backend:** Run `mvn spring-boot:run` in `ebook-chat/`. (Spring Boot 1.5 with Java 9+ does not support DevTools auto-restart; restart the backend manually after code changes.)

**One command (both at once):** From project root run `./run-dev.sh`. This starts the backend in the background and the frontend in the foreground. Ctrl+C stops both. Ensure dependencies are up first (e.g. `docker-compose -f docker-compose/dependencies.yml up -d mysql redis cassandra rabbitmq-stomp minio`).

**Two terminals (recommended if download says "cannot reach server"):**
- **Terminal 1 (backend):** From project root run `./start-backend.sh` (or `cd ebook-chat && mvn spring-boot:run`). Leave it running until you see "Started Application" or "Tomcat started on port(s): 8080". Then open http://localhost:8080 in a browser to confirm.
- **Terminal 2 (frontend):** From project root run `cd frontend && npm run dev`. Then use the app at http://localhost:3000; download will work once the backend is up.

**"Download failed: cannot reach server"** — The frontend cannot reach the backend. (1) Start the backend: `./run-dev.sh` or `cd ebook-chat && mvn spring-boot:run`. (2) Wait until Spring Boot has fully started (e.g. "Started Application"). (3) For a **permanent** API URL config: copy `frontend/.env.example` to `frontend/.env.local`, edit if needed (default is `http://localhost:8080`), then restart the frontend. If `./run-dev.sh` fails with "Permission denied", run `chmod +x run-dev.sh` once.

# Spring boot Run

docker-compose -f docker-compose/dependencies.yml up -d --build ebook-chat-app


cd ebook-chat
mvn spring-boot:run

cd "/Users/sotsirymony/Desktop/Chat System asesstment II/chat-app-spring-websocket-cassandra-redis-rabbitmq/ebook-chat" && mvn spring-boot:run

docker-compose -f docker-compose/dependencies.yml up -d mysql redis cassandra rabbitmq-stomp minio

# Re-build jar and run spring boot
cd ebook-chat
mvn clean package -DskipTests

java -jar target/ebook-chat-*.jar


cd "/Users/sotsirymony/Desktop/Chat System asesstment II/chat-app-spring-websocket-cassandra-redis-rabbitmq/ebook-chat" && mvn clean package -DskipTests && java -jar target/ebook-chat-*.jar
