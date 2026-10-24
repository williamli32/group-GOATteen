# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim AS build
WORKDIR /build
ENV NG_CLI_ANALYTICS=false
COPY package.json package-lock.json ./
RUN npm ci --include=dev
COPY . .
RUN npm run build -- --configuration production

FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /build/dist/trading-platform-ui/browser /srv
EXPOSE 80 443
