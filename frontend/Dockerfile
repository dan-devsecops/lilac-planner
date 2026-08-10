# build stage
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci --no-audit --no-fund

COPY . .

# Keycloak connection - baked into the SPA at build time.
# Override via docker-compose build args or CI build args for production.
ARG VITE_KEYCLOAK_URL=http://localhost:8080
ARG VITE_KEYCLOAK_REALM=lilac-planner
ARG VITE_KEYCLOAK_CLIENT_ID=lilac-planner-app
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL
ENV VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM
ENV VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID

RUN npm run build

# runtime stage
# nginx-unprivileged runs as uid 101 (non-root) and listens on 8080 by default.
FROM nginxinc/nginx-unprivileged:alpine
# The html root is root-owned in the base image, so uid 101 cannot create files there.
# Switch to root solely to fix ownership, then drop back - entrypoint.sh needs write
# access to write config.js before nginx starts.
USER root
RUN chown 101:0 /usr/share/nginx/html
USER 101
COPY --from=build --chown=101:0 /app/dist /usr/share/nginx/html

# Templated nginx config: envsubst substitutes BACKEND_URL at container start.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
ENV BACKEND_URL=http://backend:8090

# Runtime SPA config: entrypoint.sh writes config.js from AUTH_PROVIDER before nginx starts.
# (Legacy AUTH_ENABLED is still honoured as a fallback.)
USER root
COPY entrypoint.sh /docker-entrypoint-lilac.sh
RUN chmod 755 /docker-entrypoint-lilac.sh
USER 101
ENV AUTH_PROVIDER=none

EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint-lilac.sh"]
CMD ["nginx", "-g", "daemon off;"]
