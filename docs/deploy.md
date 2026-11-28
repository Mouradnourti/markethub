# Deploying MarketHub

Two supported ways: Docker Compose on any server (one machine, everything included), or the Render Blueprint
(`render.yaml`, managed database and HTTPS). Neither needs code changes: everything is configured with environment
variables.

## Free hosting (recommended): Oracle Cloud Always Free

The whole stack (storefront, backend, AI, e-mails, PostgreSQL, Redis) runs unchanged on one free ARM server,
with HTTPS and a free host name. Unlike free "platform" tiers, nothing sleeps after 15 minutes, uploaded images
survive restarts and live notifications keep working.

Why not a free platform tier: Vercel cannot run the Java, Python or PHP services; Render's free web services sleep
when idle, share 750 hours a month between all services, have 512 MB of memory, lose files on restart and their free
database expires after 30 days. Splitting MarketHub across several of those would cripple features.

**1. Create the account (about 10 minutes).** Sign up at oracle.com/cloud/free. A bank card is asked to verify
your identity; Always Free resources are not charged. Pick a home region close to your users; it cannot be changed.

**2. Create the server.** Console → Compute → Instances → Create instance:
- Image: **Canonical Ubuntu 24.04**.
- Shape: **Ampere → VM.Standard.A1.Flex**, 2 OCPU and 12 GB memory (MarketHub needs about 4 GB; Oracle reportedly
  lowered the free ARM allowance from 4 OCPU / 24 GB to 2 OCPU / 12 GB in 2026, so stay at or below that).
- Networking: keep "Assign a public IPv4 address".
- SSH keys: download the generated private key (or paste your own public key).
- If Oracle answers "Out of capacity", try another availability domain or try again later.

**3. Open the web ports in Oracle's network.** Instance → Subnet → Default security list → Add ingress rules:
source `0.0.0.0/0`, TCP, destination ports `80` and `443`.

**4. Install MarketHub with one command.**

```bash
ssh -i <private-key> ubuntu@<public-ip>
curl -fsSLO https://raw.githubusercontent.com/Mouradnourti/markethub/main/deploy/setup-server.sh
bash setup-server.sh
```

The script installs Docker, opens ports 80/443 in the server firewall, clones the repository, generates a `.env`
with random secrets and starts everything behind Caddy, which gets a Let's Encrypt certificate. Without arguments
the address is `https://<ip-with-dashes>.sslip.io` (free, nothing to configure). With your own domain, point its
DNS A record to the server, then run `bash setup-server.sh shop.example.com`.

The first build takes 10 to 20 minutes, then the demo data is seeded on first start. Sign in with the demo accounts
from the README.

**Updating after a new push:** run `bash setup-server.sh` again (it pulls and rebuilds, keeping `.env`).

**Good to know:**
- E-mails are caught by Mailpit on the server (not delivered). Open them through an SSH tunnel:
  `ssh -i <private-key> -L 8025:localhost:8025 ubuntu@<public-ip>`, then http://localhost:8025. Add SMTP settings to
  the notification service to really send them.
- Monitoring: `sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile monitoring up -d`,
  then tunnel port 3000 the same way for Grafana (password: `GRAFANA_ADMIN_PASSWORD` in `.env`). Admin UIs listen on
  the server's loopback only, never on the public address.
- Oracle may reclaim Always Free instances that stay almost completely idle for a week; a demo that gets visits
  and the backend's scheduled jobs keep it active. Keep a copy of `.env`.

**Free alternative for students:** the GitHub Student Developer Pack (education.github.com/pack) has included cloud
credits (for example DigitalOcean or Azure for Students). The same script works on any Ubuntu 24.04 server with
4 GB of memory or more.

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
