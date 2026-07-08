# =================================================================
# Multi-stage Dockerfile
#
# STAGE 1 (build) has two consumers:
#   - This Dockerfile itself: produces the jar for stage 2
#   - docker-compose.dev.yml: uses `target: build` as a dev
#     environment with Maven + hot-reload (inotify-tools)
#
# STAGE 2 (runtime) is what CI deploys: a slim JRE-only image.
# =================================================================

# STAGE 1: Build stage — full Maven + JDK image
FROM maven:3.9-eclipse-temurin-21 AS build

# inotify-tools is used by docker-compose.dev.yml for hot-reload.
# Baking it here (instead of apt-get on every container start) makes
# `docker compose up` fast and network-independent.
# It does NOT leak into the runtime image (stage 2 starts FROM scratch base).
RUN apt-get update \
    && apt-get install -y --no-install-recommends inotify-tools \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy pom.xml alone first, then resolve dependencies.
# Docker caches this layer: as long as pom.xml is unchanged,
# rebuilds skip the (slow) dependency download entirely.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

# Source code changes often — copy it last so only these layers rebuild.
COPY src ./src
RUN mvn -B -ntp package -DskipTests
# Tests are skipped here because CI already ran them in the
# Test & Package stage. This build exists to produce the artifact.

# STAGE 2: Runtime stage — slim JRE, no Maven, no source code
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Run as a non-root user (security best practice).
# If the container is ever compromised, the attacker is not root.
RUN useradd -r -u 1001 appuser

COPY --from=build /app/target/*.jar app.jar

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]