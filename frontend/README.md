# AshaAI — Frontend

The AshaAI frontend is a **React 19 Progressive Web App (PWA)** built with Vite, Tailwind CSS, and Zustand.

A single codebase provides two portals:

- **ASHA Worker App** — designed for field use on mobile devices
- **Admin/Supervisor Dashboard** — designed for web-based management and supervision

The frontend **never writes application data directly to Supabase**. All application-data writes go through the Spring Boot backend.

At startup, the application fetches its non-secret runtime configuration from the backend's:

```http
GET /api/public-config
```

This includes the Supabase URL/anon key, Google Maps configuration, and NGO form URLs.

For the complete system architecture, see the [root README](../README.md).

## Prerequisites

- **Node.js 20+ or 22+**
- **npm**
- The **AshaAI backend** running locally or deployed

The frontend depends on the backend for its API and runtime configuration.

See [`../backend/README.md`](../backend/README.md) for backend setup.

## 1. Enter the folder and install dependencies

From the repository root:

```bash
cd ashaai-v2/frontend
npm install --legacy-peer-deps
```

The `--legacy-peer-deps` flag is currently required because the peer-dependency range used by `vite-plugin-pwa` does not officially cover every Vite major version this project may use.

## 2. Configure the backend URL

Create:

```text
frontend/.env.local
```

Add:

```env
VITE_BACKEND_URL=http://localhost:8080
```

This is the **only runtime endpoint configuration required by the frontend**.

### Important

`VITE_BACKEND_URL` is a **build-time value**. Vite embeds it into the frontend bundle when the application is built.

If you change it:

- Restart the development server during development
- Rebuild the application for production

Other configuration such as:

- Supabase URL
- Supabase anon key
- Google Maps key
- NGO form URLs

is fetched from the backend's `/api/public-config` endpoint.

**Do not place additional secrets in frontend environment files.**

## 3. Start the development server

Run:

```bash
npm run dev
```

The application will be available at:

```text
http://localhost:5173
```

### Authentication

The application supports two login flows:

**ASHA Worker**

```text
Phone Number → OTP → ASHA Worker Portal
```

**Admin / Supervisor**

```text
Email + Password → Admin/Supervisor Portal
```

## 4. Build for production

Create a production build with:

```bash
npm run build
```

The compiled application is generated in:

```text
frontend/dist/
```

To preview the production build locally:

```bash
npm run preview
```

## 5. Run linting

Run the project's lint checks with:

```bash
npm run lint
```

## PWA and offline support

AshaAI is built as a **Progressive Web App** for field environments where network connectivity may be unreliable.

The frontend uses:

- **Service Worker** for offline application support
- **IndexedDB** for local data and queued operations
- **Write queue** for operations performed while offline
- Automatic replay of queued writes when connectivity is restored

The general flow is:

```text
ASHA Worker
     │
     ▼
React PWA
     │
     ├── Online ──► Backend ──► Supabase
     │
     └── Offline
            │
            ▼
       IndexedDB Queue
            │
            ▼
     Connectivity Restored
            │
            ▼
         Backend
```

The frontend does not bypass the backend when replaying queued writes.

## Portal architecture

Both portals are part of the same React application and production build.

The rendered experience is determined by the authenticated user's role.

```text
                    React 19 PWA
                         │
              ┌──────────┴──────────┐
              │                     │
        ASHA Worker             Admin/Supervisor
              │                     │
         ASHAGuard.jsx          AdminGuard.jsx
              │                     │
              ▼                     ▼
        Worker Portal          Admin Dashboard
```

The main route guards are:

```text
ASHAGuard.jsx
AdminGuard.jsx
```

These guards determine which protected portal and layout the authenticated user can access.

## Security rules

### No direct database writes

The frontend must never write application data directly to Supabase.

```text
Frontend → Backend → Supabase
```

Not:

```text
Frontend → Supabase
```

### No secrets in the frontend

Never place private credentials, API secrets, service-role keys, or other sensitive values anywhere under:

```text
frontend/
```

If a browser-accessible configuration value is genuinely required, it must be provided through the backend's public configuration endpoint rather than stored as a private environment variable.

The frontend should be treated as a **public client**: anything bundled into the application can potentially be inspected by a user.