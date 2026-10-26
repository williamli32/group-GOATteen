# Keep the PostgreSQL major version at 17. Alpine replaces the affected Debian
# runtime packages and uses su-exec instead of the old Go-based gosu helper.
FROM postgres:17.11-alpine3.24
RUN apk upgrade --no-cache

# Refuse an unmarked existing cluster: changing libc underneath an existing
# Debian cluster can change collation/index behaviour. Migrate by dump/restore.
COPY leap-entrypoint.sh /usr/local/bin/leap-entrypoint.sh
COPY 10-app-user.sql /docker-entrypoint-initdb.d/10-app-user.sql
COPY 20-runtime-marker.sh /docker-entrypoint-initdb.d/20-runtime-marker.sh
RUN chmod 755 /usr/local/bin/leap-entrypoint.sh /docker-entrypoint-initdb.d/20-runtime-marker.sh
ENTRYPOINT ["/usr/local/bin/leap-entrypoint.sh"]
CMD ["postgres"]
