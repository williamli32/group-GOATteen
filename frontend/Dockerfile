# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim AS build
WORKDIR /build
ENV NG_CLI_ANALYTICS=false
COPY package.json package-lock.json ./
RUN npm ci --include=dev
COPY . .
RUN npm run build -- --configuration production

# Rebuild the proxy with the patched Go runtime and x/net module. Updating APKs
# alone cannot update Go libraries compiled into the upstream Caddy binary.
FROM golang:1.27.3-alpine3.24 AS proxy-build
WORKDIR /proxy
ENV GOTOOLCHAIN=local
RUN go mod init leap-proxy-build \
    && go get github.com/caddyserver/caddy/v2/cmd/caddy@v2.11.7 \
    && go get golang.org/x/net@v0.60.0 \
    && CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" \
       -o /out/caddy github.com/caddyserver/caddy/v2/cmd/caddy \
    && cp /go/pkg/mod/github.com/caddyserver/caddy/v2@v2.11.7/LICENSE /out/CADDY-LICENSE

FROM alpine:3.24
RUN apk upgrade --no-cache && apk add --no-cache ca-certificates mailcap
ENV XDG_CONFIG_HOME=/config XDG_DATA_HOME=/data
WORKDIR /srv
COPY --from=proxy-build /out/caddy /usr/bin/caddy
COPY --from=proxy-build /out/CADDY-LICENSE /usr/share/licenses/caddy/LICENSE
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /build/dist/trading-platform-ui/browser /srv
EXPOSE 80 443
VOLUME ["/data", "/config"]
CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
