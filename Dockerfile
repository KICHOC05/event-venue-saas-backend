# syntax=docker/dockerfile:1

# The project compiles against Java 25. Maven itself is provided by the
# repository wrapper, which pins Maven 3.9.12 in maven-wrapper.properties.
ARG JAVA_BUILD_IMAGE=eclipse-temurin:25-jdk-alpine
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:25-jre-alpine

FROM ${JAVA_BUILD_IMAGE} AS build

RUN apk add --no-cache curl unzip

WORKDIR /workspace

# Resolve dependencies in a separate layer so source changes do not download
# the complete Maven dependency graph again.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod 0755 mvnw \
    && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/

# Tests belong in CI. The image build still compiles test sources while
# skipping their execution, then produces the executable Spring Boot JAR.
RUN ./mvnw --batch-mode --no-transfer-progress clean package -DskipTests \
    && JAR_FILE="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' -print -quit)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" /workspace/application.jar

FROM ${JAVA_RUNTIME_IMAGE} AS runtime

RUN apk add --no-cache tzdata \
    && addgroup --system --gid 10001 app \
    && adduser --system --uid 10001 --ingroup app --home /app --disabled-password app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/application.jar /app/application.jar

# Only non-sensitive defaults belong in the image. Inject DB_PASSWORD,
# APP_JWT_SECRET and CLOUDINARY_API_SECRET at runtime through the platform's
# secret manager or docker --env-file; never COPY a local .env into the image.
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_FORWARD_HEADERS_STRATEGY=NONE \
    JPA_DDL_AUTO=validate \
    JPA_SHOW_SQL=false \
    TZ=America/Mexico_City \
    SERVER_SHUTDOWN=graceful \
    SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=30s \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

USER app:app

EXPOSE 8080

# A TCP check does not require exposing an unauthenticated application endpoint.
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD nc -z 127.0.0.1 "$SERVER_PORT" || exit 1

STOPSIGNAL SIGTERM

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
