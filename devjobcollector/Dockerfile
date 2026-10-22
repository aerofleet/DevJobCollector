# syntax=docker/dockerfile:1.7

FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY auth-common ./auth-common
COPY src ./src

RUN chmod +x gradlew \
    && ./gradlew clean bootJar resumeExtractorJar --no-daemon \
    && jar_path="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' ! -name 'resume-extractor.jar' -print -quit)" \
    && test -n "$jar_path" \
    && cp "$jar_path" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime-base

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl tesseract-ocr tesseract-ocr-kor tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

USER app

ENV SPRING_PROFILES_ACTIVE=prod
ENV AI_PREPROCESSING_WORKER_JAR=/app/resume-extractor.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=12 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# CI packages the exact JARs built alongside the tests on the native runner.
# BuildKit skips the unrelated source builder when --target prebuilt is selected.
FROM runtime-base AS prebuilt
COPY --chown=app:app .codex/deploy-image/app.jar ./app.jar
COPY --chown=app:app .codex/deploy-image/resume-extractor.jar ./resume-extractor.jar

# Keep the default target self-contained for local builds and pull-request CI.
FROM runtime-base AS runtime
COPY --from=builder --chown=app:app /workspace/app.jar ./app.jar
COPY --from=builder --chown=app:app /workspace/build/libs/resume-extractor.jar ./resume-extractor.jar
