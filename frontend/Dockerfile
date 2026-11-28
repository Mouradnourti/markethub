# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# --- Runtime stage: nginx serves the SPA and proxies the API ---
FROM nginx:1.27-alpine
# nginx fills ${BACKEND_HOSTPORT} from the environment at start (docker-compose: backend:8080; Render: private host).
ENV BACKEND_HOSTPORT=backend:8080
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://localhost/ >/dev/null || exit 1
