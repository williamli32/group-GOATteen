#!/usr/bin/env bash
# Run from the repository root on the Linux Docker host (also used by Jenkins).
set -eu
mkdir -p security-reports
rm -f security-reports/*.json
scanner="aquasec/trivy:0.76.1"
tag="goal1-$(date +%s)-$$"
cache="leap-security-$tag"
images=""
cleanup() {
    for image in $images; do docker image rm "$image" >/dev/null 2>&1 || true; done
    docker volume rm "$cache" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker volume create "$cache" >/dev/null
failed=0
for component in trading-platform market-data-service frontend postgres; do
    case "$component" in
        trading-platform) context=backend/trading-platform ;;
        market-data-service) context=backend/market-data-service ;;
        frontend) context=frontend ;;
        postgres) context=deploy/postgres ;;
    esac
    image="leap-security/$component:$tag"
    images="$images $image"
    docker build --pull -t "$image" "$context"
    # Reports retain ALL high/critical findings, including those without an upstream fix.
    # The exit status is enforced; neither scanner outages nor findings turn a build green.
    if ! docker run --rm \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v "$cache:/root/.cache/trivy" \
        "$scanner" image --scanners vuln --severity HIGH,CRITICAL \
        --timeout 10m --exit-code 1 --format json "$image" \
        > "security-reports/$component.json"; then
        failed=1
    fi
done
exit "$failed"
