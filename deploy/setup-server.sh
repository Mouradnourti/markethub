#!/usr/bin/env bash
# One-command install of MarketHub on a fresh Ubuntu server (tested target: Oracle Cloud Always Free, Ampere ARM).
#
#   curl -fsSLO https://raw.githubusercontent.com/<you>/markethub/main/deploy/setup-server.sh   # or copy it over
#   bash setup-server.sh                       # domain defaults to <public-ip>.sslip.io
#   bash setup-server.sh shop.example.com      # or your own domain (its DNS A record must point to this server)
#
# What it does: installs Docker from Ubuntu's packages, opens ports 80/443 in the server firewall, clones the
# repository, writes a .env with fresh random secrets, then builds and starts the stack behind Caddy (HTTPS).
# Safe to run again: an existing .env is kept and the stack is rebuilt with the latest code.
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/Mouradnourti/markethub.git}"
APP_DIR="${APP_DIR:-$HOME/markethub}"
DOMAIN_ARG="${1:-}"

log() { printf '\n\033[1;35m==> %s\033[0m\n' "$*"; }

log "Installing Docker, Git and OpenSSL"
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose-v2 git openssl curl
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER" || true
# docker-compose.prod.yml uses the "!reset" tag, added in Compose 2.24 (Ubuntu 24.04 ships a recent enough one); "!override" needs 2.24.4.
COMPOSE_VERSION="$(sudo docker compose version --short | sed 's/^v//')"
if [ "$(printf '%s\n' 2.24.4 "$COMPOSE_VERSION" | sort -V | head -1)" != "2.24.4" ]; then
  echo "Docker Compose $COMPOSE_VERSION is too old (2.24.4+ needed). Use an Ubuntu 24.04 image." >&2
  exit 1
fi

log "Opening ports 80 and 443 in the server firewall"
# Oracle's Ubuntu images ship iptables rules that reject everything but SSH.
for port in 80 443; do
  if ! sudo iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null; then
    sudo iptables -I INPUT 1 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
  fi
done
if command -v netfilter-persistent >/dev/null; then
  sudo netfilter-persistent save
else
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y iptables-persistent && sudo netfilter-persistent save
fi
if command -v ufw >/dev/null && sudo ufw status | grep -q active; then
  sudo ufw allow 80/tcp && sudo ufw allow 443/tcp
fi

log "Getting the code"
if [ -d "$APP_DIR/.git" ]; then
  git -C "$APP_DIR" pull --ff-only
else
  git clone "$REPO_URL" "$APP_DIR"
fi
cd "$APP_DIR"

if [ -n "$DOMAIN_ARG" ]; then
  DOMAIN="$DOMAIN_ARG"
else
  PUBLIC_IP="$(curl -fsS https://api.ipify.org || true)"
  if [ -z "$PUBLIC_IP" ]; then
    echo "Could not detect the public IP. Run again with the domain: bash setup-server.sh shop.example.com" >&2
    exit 1
  fi
  # sslip.io resolves "<ip-with-dashes>.sslip.io" to that IP: a free host name that can get an HTTPS certificate.
  DOMAIN="${PUBLIC_IP//./-}.sslip.io"
fi

# Sets KEY=value in .env, replacing an existing line or appending one.
set_env() {
  local key="$1" value="$2"
  if grep -q "^${key}=" .env; then
    awk -v k="$key" -v v="$value" 'BEGIN { FS = OFS = "=" } $1 == k { print k "=" v; next } { print }' .env > .env.tmp
    mv .env.tmp .env
  else
    printf '%s=%s\n' "$key" "$value" >> .env
  fi
}

if [ ! -f .env ]; then
  log "Writing .env with fresh secrets"
  cp .env.example .env
  set_env POSTGRES_PASSWORD "$(openssl rand -hex 24)"
  set_env JWT_SECRET "$(openssl rand -hex 48)"
  set_env INTERNAL_API_KEY "$(openssl rand -hex 32)"
  set_env DATA_ENCRYPTION_KEY "$(openssl rand -hex 32)"
  set_env NOTIFICATION_APP_KEY "base64:$(openssl rand -base64 32)"
  set_env GRAFANA_ADMIN_PASSWORD "$(openssl rand -hex 16)"
  chmod 600 .env
else
  log "Keeping the existing .env"
fi
set_env DOMAIN "$DOMAIN"
set_env STOREFRONT_URL "https://$DOMAIN"
set_env CORS_ALLOWED_ORIGINS "https://$DOMAIN"

log "Building and starting MarketHub (the first build takes 10 to 20 minutes)"
sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

log "Done"
cat <<EOF
MarketHub is starting at: https://$DOMAIN
The backend seeds the demo data on first start (about a minute after the containers are up).
Follow the startup with:  cd $APP_DIR && sudo docker compose logs -f backend
Secrets are in $APP_DIR/.env (keep a copy somewhere safe).
EOF
