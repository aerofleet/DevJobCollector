#!/usr/bin/env bash
set -euo pipefail

container="djc-member-migration-${GITHUB_RUN_ID:-local}-$$"
root_password="djc-migration-test-root"
mysql_image="${DJC_MIGRATION_TEST_IMAGE:-mysql:26.7.0}"

cleanup() {
  docker rm --force "$container" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run --detach \
  --name "$container" \
  --publish 127.0.0.1::3306 \
  --env MYSQL_ROOT_PASSWORD="$root_password" \
  --env MYSQL_DATABASE=devjob \
  "$mysql_image" >/dev/null

for attempt in $(seq 1 30); do
  if docker exec --env MYSQL_PWD="$root_password" "$container" \
      mysqladmin ping --host 127.0.0.1 --user root --silent; then
    break
  fi
  if [ "$attempt" = '30' ]; then
    echo 'MySQL did not become ready' >&2
    exit 1
  fi
  sleep 2
done

host_port="$(docker port "$container" 3306/tcp | sed 's/.*://')"
test -n "$host_port"

DJC_MIGRATION_TEST_URL="jdbc:mysql://127.0.0.1:${host_port}/devjob?serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true" \
DJC_MIGRATION_TEST_USERNAME=root \
DJC_MIGRATION_TEST_PASSWORD="$root_password" \
DJC_MIGRATION_TEST_EXPECTED_VERSION=26.7.0 \
  ./gradlew test --tests kr.itsdev.devjobcollector.migration.MemberV3MigrationTest --no-daemon
