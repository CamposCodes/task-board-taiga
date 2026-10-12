# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src

COPY --from=certs . /certs/
RUN for crt in /certs/*.crt; do \
      [ -f "$crt" ] && keytool -importcert -noprompt -cacerts -storepass changeit -alias "$(basename "$crt")" -file "$crt"; \
    done; true

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src
RUN --mount=type=cache,target=/root/.gradle sh gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir -p /data/files && chown app:app /data/files
ENV APP_FILES_DIR=/data/files
USER app
WORKDIR /app
COPY --from=build /src/build/libs/taskboard.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
