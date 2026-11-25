# Deploying FinSight

FinSight is a **Spring Boot 3.5 + Vaadin Flow (Java 21)** app. It needs one always-running Java process and a
PostgreSQL database, so it can **not** run on Vercel (Vercel only hosts static sites and short serverless functions).
Use a container host such as Render, Railway or Fly.io. This repo is ready for **Render** (see `render.yaml`).

## Option A - Render Blueprint (easiest)
1. Push this folder to a GitHub repo (see README.md).
2. Go to https://dashboard.render.com -> **New -> Blueprint** -> connect GitHub -> pick the repo.
3. Render reads `render.yaml` and creates:
   - a Docker web service `finsight` (built from `finsight-backend/Dockerfile`)
   - a free PostgreSQL database `finsight-db`
   - all environment variables (DB connection + an auto-generated `FINSIGHT_JWT_SECRET`)
4. Click **Apply**. The first build takes roughly 5-15 minutes (Vaadin builds its frontend bundle).
5. Open the `https://finsight-xxxx.onrender.com` link. Flyway creates the database tables on first start.

### Free-tier limits (check https://render.com/docs/free, they change)
- A free web service sleeps after 15 minutes without traffic; the next visit takes up to about a minute to wake it.
- A free Render Postgres database **expires 30 days after creation** (14-day grace period to upgrade, then it is deleted).
  Export your data or upgrade before then, or use an external database (Option B).
- Free instances have little memory (about 512 MB). If the app is killed or the build runs out of memory,
  switch the web service to a paid instance (Starter).

## Option B - Render web service + your own database (Neon, Supabase, ...)
Create the web service manually (New -> Web Service -> Docker, Dockerfile path `finsight-backend/Dockerfile`,
Docker context `finsight-backend`) and set these variables yourself:

| Variable | Example |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (the image already sets this) |
| `DB_URL` | `jdbc:postgresql://HOST:5432/DBNAME?sslmode=require` (must start with `jdbc:`) |
| `DB_USERNAME` / `DB_PASSWORD` | database credentials |
| `FINSIGHT_JWT_SECRET` | 32+ random characters, e.g. `openssl rand -base64 48` |
| `DB_POOL_SIZE`, `FINSIGHT_API_DOCS_ENABLED` | optional |

`PORT` is set by the host automatically. Health check path: `/actuator/health`.

## Notes
- Use a **single instance**: Vaadin sessions and the login throttle live in memory.
- The container filesystem is temporary on free plans (uploaded files and local ML model files are lost on restart).
- Never commit `.env`. Use `finsight-backend/.env.example` as the template for local development.

## Run locally
```
cd finsight-backend
copy .env.example .env      (macOS/Linux: cp .env.example .env) and fill in your local PostgreSQL details
mvn spring-boot:run         -> http://localhost:8080
```
Needs JDK 21, Maven and a local PostgreSQL database named `finsight`.
