# Deploying MarketHub

Two supported ways: Docker Compose on any server (one machine, everything included), or the Render Blueprint
(`render.yaml`, managed database and HTTPS). Neither needs code changes: everything is configured with environment
variables.

## Option A: a server with Docker (VPS)

Any Linux VM with 2 vCPU / 4 GB RAM (Hetzner, OVH, DigitalOcean, Azure for Students…).

```bash
git clone https://github.com/<you>/markethub.git && cd markethub
cp .env.example .env        # then edit every "change-me" value
docker compose up -d --build
docker compose --profile monitoring up -d   # optional: Prometheus :9090 and Grafana :3000
```

Put a reverse proxy with HTTPS in front of port 4200 (Caddy is the shortest: `markethub.example.com { reverse_proxy
localhost:4200 }`), then set in `.env`:

| Variable | Value |
|---|---|
| `STOREFRONT_URL` | `https://markethub.example.com` (links in e-mails) |
| `CORS_ALLOWED_ORIGINS` | `https://markethub.example.com` |
| `JWT_SECRET`, `INTERNAL_API_KEY`, `DATA_ENCRYPTION_KEY` | long random values (`openssl rand -base64 48`) |
| `SPRING_PROFILES_ACTIVE` | `docker,demo` for a demo with seed data, `docker` for an empty marketplace |

Only ports 4200 (or 80/443 through the proxy) need to be reachable. The actuator (health and metrics) listens on
8081 inside the Docker network and is never published.

## Option B: Render Blueprint

1. Push the repository to GitHub.
2. Render dashboard → **New → Blueprint** → pick the repository. Render reads `render.yaml` and creates: PostgreSQL,
   Key Value (Redis), the backend, AI and e-mail services (private network) and the public storefront.
3. Fill the values Render asks for:
   - `STOREFRONT_URL` and `CORS_ALLOWED_ORIGINS`: the storefront URL Render shows (`https://markethub-web.onrender.com`).
   - `APP_KEY` of the e-mail service: `base64:` followed by 32 random bytes in base64.
   - `ANTHROPIC_API_KEY` (optional): turns on the Claude description writer; leave empty for the built-in writer.
   - Stripe test keys (optional): leave empty to keep the payment simulator.
4. Open the storefront URL. The first start of the backend seeds the demo data (about a minute).

Plans: the storefront and the cache fit the free plan. The backend needs a paid instance because it keeps
uploaded images on a persistent disk; the private AI and e-mail services also run on paid instances (Render has no
free private services). Check Render's current prices before creating the Blueprint. E-mails are written to the
e-mail service logs (`MAIL_MAILER=log`) until SMTP settings are added.

## After deploying

- Sign in with the demo accounts from the README, then turn on two-step verification for the admin account
  (avatar menu → Sign-in & security).
- `GET /actuator/health` on the backend's internal address should report `UP`.
- Change the demo passwords, or deploy without the `demo` profile and create the admin with `ADMIN_EMAIL` /
  `ADMIN_PASSWORD`.

## Limits of a single instance

Live notifications (server-sent events), the sign-in rate limit and stream tickets are kept in memory, so the
backend runs as one instance. Running several would need a Redis pub/sub relay for events and a shared rate-limit
store; both are noted in `docs/architecture.md`.
