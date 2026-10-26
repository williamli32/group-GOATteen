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

# A mirror layer can return 404 without activating Trivy's transient-error fallback.
# Warm BOTH databases from official registries before building or scanning images.
# Try each registry explicitly so 404s, as well as 429/5xx errors, get a fallback.
download_database() {
    local label="$1" download_flag="$2" repository_flag="$3" artifact="$4"
    local registry attempt
    for registry in ghcr.io/aquasecurity public.ecr.aws/aquasecurity; do
        for attempt in 1 2; do
            echo "Downloading $label database from $registry (attempt $attempt/2)..."
            if docker run --rm \
                -v "$cache:/root/.cache/trivy" \
                "$scanner" image "$download_flag" \
                "$repository_flag" "$registry/$artifact" \
                --timeout 5m --no-progress; then
                return 0
            fi
            if [ "$attempt" -lt 2 ]; then sleep 2; fi
        done
    done
    echo "ERROR: Unable to download the $label database. Security checks cannot run." >&2
    return 1
}

download_database vulnerability --download-db-only --db-repository trivy-db:2
download_database Java --download-java-db-only --java-db-repository trivy-java-db:1

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
    status=0
    docker run --rm \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v "$cache:/root/.cache/trivy" \
        "$scanner" image --scanners vuln --severity HIGH,CRITICAL \
        --skip-db-update --skip-java-db-update \
        --timeout 10m --exit-code 10 --format json "$image" \
        > "security-reports/$component.json" || status=$?

    # Exit 10 means findings; other nonzero statuses mean the scan itself failed.
    case "$status" in
        0) echo "PASS: $component has no HIGH/CRITICAL findings." ;;
        10) echo "FAIL: $component has HIGH/CRITICAL findings."; failed=1 ;;
        *) echo "ERROR: $component scan failed (exit $status)." >&2; failed=1; continue ;;
    esac

    # Render the same report in the console. No second scan or database download.
    if [ ! -s "security-reports/$component.json" ]; then
        echo "ERROR: Missing scan report for $component." >&2
        failed=1
    elif ! docker run --rm \
        -v "$PWD/security-reports:/reports:ro" \
        "$scanner" convert --format table --severity HIGH,CRITICAL \
        --exit-code 0 "/reports/$component.json"; then
        echo "ERROR: Cannot display the scan report for $component." >&2
        failed=1
    fi
done
exit "$failed"
