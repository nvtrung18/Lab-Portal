FROM node:20-alpine AS frontend-build

WORKDIR /app
COPY client/package*.json ./
RUN npm ci
COPY client/ ./
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM caddy:2-alpine
COPY --from=frontend-build /app/dist /srv/frontend
COPY docker/caddy/Caddyfile /etc/caddy/Caddyfile
