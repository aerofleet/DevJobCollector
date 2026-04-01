#!/usr/bin/env bash
set -euo pipefail

if [ -f /home/ubuntu/djc.env ]; then
  set -a
  source /home/ubuntu/djc.env
  set +a
fi

exec /usr/bin/java -jar /home/ubuntu/app.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url="jdbc:mysql://10.0.0.187:3306/devjob?serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true" \
  --spring.datasource.username="dbadmin" \
  --spring.datasource.password="${DB_PASSWORD:-}" \
  --data-api.public-data.service-key="${API_SERVICE_KEY:-}" \
  --spring.jpa.hibernate.ddl-auto=none \
  --spring.security.oauth2.client.registration.google.client-id="${GOOGLE_CLIENT_ID:-}" \
  --spring.security.oauth2.client.registration.google.client-secret="${GOOGLE_CLIENT_SECRET:-}" \
  --spring.security.oauth2.client.registration.github.client-id="dummy" \
  --spring.security.oauth2.client.registration.github.client-secret="dummy"
