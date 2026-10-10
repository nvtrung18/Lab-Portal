FROM node:22-alpine AS frontend-build

WORKDIR /app
COPY client/package*.json ./
RUN npm ci
COPY client/ ./
COPY contracts/ /contracts/
ARG VITE_API_BASE_URL
ARG VITE_GOOGLE_CLIENT_ID=
ARG VITE_DEFAULT_LOCALE=vi
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_GOOGLE_CLIENT_ID=${VITE_GOOGLE_CLIENT_ID} \
    VITE_DEFAULT_LOCALE=${VITE_DEFAULT_LOCALE}
RUN npm run build

FROM caddy:2-alpine
COPY --from=frontend-build /app/dist /srv/frontend
COPY docker/caddy/Caddyfile /etc/caddy/Caddyfile
