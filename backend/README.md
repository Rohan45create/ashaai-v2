# AshaAI — Backend

The AshaAI backend is a **Spring Boot 3.5 (Java 21)** service and the only component that writes application data to the database.

The frontend does **not** write directly to Supabase. The AI service is also isolated from the database and is called only by the backend.

For the complete system architecture, see the [root README](../README.md) and [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

## Prerequisites

- **Java 21** — Temurin is recommended
- **Maven** — the Maven Wrapper is included, so a separate Maven installation is not required
- **Supabase project** with PostgreSQL and Auth
- **ai-service** running locally if you want to test AI features end-to-end

Supabase connection details are available under:

**Project Settings → API**  
**Project Settings → Database**

For AI service setup, see [`../ai-service/README.md`](../ai-service/README.md).

## 1. Clone the repository

```bash
git clone https://github.com/Rohan45create/ashaai-v2.git
cd ashaai-v2/backend
```

## 2. Create the environment file

Create a file at:

```text
backend/.env
```

Add the following configuration using values from your own Supabase, Google, and Gmail accounts.

**Never commit `.env` to Git.**

```env
# Supabase
# Use the Transaction Pooler connection string on port 6543
SUPABASE_DB_URL=jdbc:postgresql://<your-project-ref>.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0
SUPABASE_DB_USERNAME=postgres.<your-project-ref>
SUPABASE_DB_PASSWORD=<your-db-password>

SUPABASE_URL=https://<your-project-ref>.supabase.co
SUPABASE_ANON_KEY=<your-anon-key>

# Google Maps
# Restrict this key to your actual frontend domain
GOOGLE_MAPS_API_KEY=<your-maps-key>

# NGO Google Forms
NGO_FORM_NEW_URL=<your-new-ngo-registration-form-url>
NGO_FORM_EXISTING_URL=<your-existing-ngo-request-form-url>
NGO_FORM_RESCHEDULE_URL=<your-reschedule-form-url>

NGO_RESCHEDULE_FORM_EMAIL_ENTRY=entry.<your-email-field-entry-id>
NGO_RESCHEDULE_FORM_DATE_ENTRY=entry.<your-date-field-entry-id>

GOOGLE_FORM_SECRET=<secret-shared-with-your-apps-script>

# AI microservice
AI_SERVICE_URL=http://localhost:8000

# Gmail SMTP
# Use a Gmail App Password, not your normal Gmail password
GMAIL_SENDER_EMAIL=<your-sending-account@gmail.com>
GMAIL_APP_PASSWORD=<your-16-char-app-password>

# Redis
# Leave blank to use in-memory session state
REDIS_URL=

SPRING_PROFILES_ACTIVE=supabase
```

### Important security notes

Never commit:

```text
.env
```

Do not place secrets anywhere under:

```text
frontend/
```

The frontend receives only the non-secret configuration it needs through:

```http
GET /api/public-config
```

## 3. Start the backend

Flyway migrations run automatically when the application starts, so no separate migration command is required.

### Linux / macOS

```bash
./mvnw spring-boot:run
```

### Windows

```bash
mvnw.cmd spring-boot:run
```

The backend runs at:

```text
http://localhost:8080
```

You can verify that the service is running with:

```bash
curl http://localhost:8080/api/public-config
```

The endpoint should return the non-secret configuration required by the frontend, such as:

- Supabase URL
- Supabase anon key
- Google Maps configuration
- NGO form URLs

## 4. Run the tests

```bash
./mvnw test
```

On Windows:

```bash
mvnw.cmd test
```

The integration tests use **Testcontainers**, so Docker must be running before executing them.

## Authentication

Supabase JWTs are validated using Supabase's **JWKS endpoint** and asymmetric signing keys.

A `SUPABASE_JWT_SECRET` is therefore **not required for normal operation**.

## Building for deployment

To build the application JAR without running the tests:

```bash
./mvnw clean package -DskipTests
```

Note that `-DskipTests` skips test execution but still compiles the test sources. A broken test source file can therefore still cause the build to fail.

## Architecture rules

The backend is the **single database write path** for application data:

```text
Frontend
   │
   ▼
Spring Boot Backend
   │
   ├──► Supabase / PostgreSQL
   │
   └──► AI Service
```

The AI service does not call the backend in reverse and does not write directly to the application database.

For the complete architecture and service responsibilities, see [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).
