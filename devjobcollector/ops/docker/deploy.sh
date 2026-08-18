#!/usr/bin/env bash
set -euo pipefail

DEPLOY_BUNDLE="${1:-/home/ubuntu/djc-deploy}"
APP_DIR="/opt/devjobcollector"
CONFIG_DIR="/etc/devjobcollector"
COMPOSE_FILE="$APP_DIR/compose.yaml"
ENV_FILE="$CONFIG_DIR/devjobcollector.env"
PROJECT_NAME="djc"

NEW_COMPOSE="$DEPLOY_BUNDLE/compose.yaml"
NEW_ENV="$DEPLOY_BUNDLE/devjobcollector.env"
GHCR_TOKEN_FILE="$DEPLOY_BUNDLE/ghcr-token"
GHCR_USERNAME_FILE="$DEPLOY_BUNDLE/ghcr-username"
ROLLBACK_ENV="$DEPLOY_BUNDLE/rollback.env"
LEGACY_COMPOSE_PROJECT="devjobcollector"
LEGACY_CONTAINER_ID=""
LEGACY_WAS_ACTIVE=false

compose() {
  docker compose --project-name "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

cleanup() {
  docker logout ghcr.io >/dev/null 2>&1 || true
  rm -f "$GHCR_TOKEN_FILE" "$GHCR_USERNAME_FILE" "$NEW_ENV" "$ROLLBACK_ENV"
}
trap cleanup EXIT

rollback() {
  echo "=== Docker deployment failed; starting rollback ==="
  compose logs --tail 120 app || true

  if [ -f "$ROLLBACK_ENV" ]; then
    install -m 0600 "$ROLLBACK_ENV" "$ENV_FILE"
  fi

  compose down || true

  if [ -n "$LEGACY_CONTAINER_ID" ]; then
    docker start "$LEGACY_CONTAINER_ID" || true
    echo "Legacy Docker Compose container restarted"
    return
  fi

  if [ "$LEGACY_WAS_ACTIVE" = "true" ]; then
    systemctl start devjobcollector.service || true
    echo "Legacy JAR/systemd service restarted"
    return
  fi

  if [ -f "$ROLLBACK_ENV" ]; then
    compose up -d --no-build --force-recreate app || true
    echo "Previous Docker image configuration restored"
  fi
}

for required_file in "$NEW_COMPOSE" "$NEW_ENV" "$GHCR_TOKEN_FILE" "$GHCR_USERNAME_FILE"; do
  if [ ! -s "$required_file" ]; then
    echo "Missing deployment file: $required_file"
    exit 1
  fi
done

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker Engine is not installed on the deployment server"
  exit 1
fi
docker compose version

GHCR_USERNAME="$(cat "$GHCR_USERNAME_FILE")"
cat "$GHCR_TOKEN_FILE" | docker login ghcr.io --username "$GHCR_USERNAME" --password-stdin

install -d -m 0755 "$APP_DIR"
install -d -m 0750 "$CONFIG_DIR"
install -m 0644 "$NEW_COMPOSE" "$COMPOSE_FILE"

if [ -f "$ENV_FILE" ]; then
  cp "$ENV_FILE" "$ROLLBACK_ENV"
  chmod 0600 "$ROLLBACK_ENV"
fi
install -m 0600 "$NEW_ENV" "$ENV_FILE"

compose config --quiet
compose pull app

if systemctl is-active --quiet devjobcollector.service; then
  LEGACY_WAS_ACTIVE=true
fi

if [ "$LEGACY_WAS_ACTIVE" = "true" ]; then
  echo "=== Stopping legacy JAR/systemd service ==="
  systemctl stop devjobcollector.service
fi

LEGACY_CONTAINER_ID="$(
  docker ps \
    --filter "label=com.docker.compose.project=$LEGACY_COMPOSE_PROJECT" \
    --filter "label=com.docker.compose.service=app" \
    --quiet \
    | head -n 1
)"

if [ -n "$LEGACY_CONTAINER_ID" ]; then
  legacy_image="$(docker inspect --format '{{.Config.Image}}' "$LEGACY_CONTAINER_ID")"
  case "$legacy_image" in
    ghcr.io/aerofleet/devjobcollector:*) ;;
    *)
      echo "Refusing to stop unexpected legacy container image: $legacy_image"
      exit 1
      ;;
  esac

  echo "=== Stopping legacy Docker Compose container ==="
  docker stop --time 30 "$LEGACY_CONTAINER_ID"
fi

if ss -H -ltn '( sport = :8080 )' | grep -q .; then
  echo "TCP 8080 is still occupied after stopping known DJC runtimes"
  ss -H -ltnp '( sport = :8080 )' || true
  rollback
  exit 1
fi

echo "=== Starting DJC container ==="
if ! compose up -d --no-build --force-recreate app; then
  rollback
  exit 1
fi

healthy=false
for attempt in $(seq 1 120); do
  if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "Container health check passed on attempt $attempt/120"
    healthy=true
    break
  fi

  container_id="$(compose ps -q app)"
  if [ -z "$container_id" ] || [ "$(docker inspect --format '{{.State.Status}}' "$container_id" 2>/dev/null || true)" = "exited" ]; then
    break
  fi
  sleep 2
done

if [ "$healthy" != "true" ]; then
  rollback
  exit 1
fi

systemctl disable devjobcollector.service >/dev/null 2>&1 || true
if [ -n "$LEGACY_CONTAINER_ID" ]; then
  docker rm "$LEGACY_CONTAINER_ID" >/dev/null
fi
compose ps
echo "DJC is running with Docker Compose"
