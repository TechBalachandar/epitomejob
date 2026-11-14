# EpitomeJob backend — W4 (contract §2b).
# Multi-stage: Maven build, then a slim JRE runtime with curl for the
# /actuator/health healthcheck. Java 21 (pom.xml: java.version=21).
# Tags verified on Docker Hub 2026-11-12:
#   maven:3.9.16-eclipse-temurin-21 (exists, 2026-09-26)
#   eclipse-temurin:21-jre (exists, 2026-11-12)

FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
# Spring Boot Maven plugin produces target/epitomejob-0.1.0.jar
# (artifactId epitomejob, version 0.1.0 in pom.xml); the glob keeps the
# Dockerfile valid if the version is bumped.
COPY --from=build /app/target/*.jar app.jar
# Phase 5A: OTel Java agent (v2.18.0, same as the gateway) — the backend's
# Micrometer OTel bridge has no OTLP exporter on the classpath, so without
# this agent no backend span would ever reach Jaeger. The agent honours
# OTEL_EXPORTER_OTLP_ENDPOINT + OTEL_SERVICE_NAME from the compose/k8s env.
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.18.0/opentelemetry-javaagent.jar /opt/otel/opentelemetry-javaagent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/otel/opentelemetry-javaagent.jar"
EXPOSE 8080
ENTRYPOINT ["java", "-Dnetworkaddress.cache.ttl=5", "-jar", "app.jar"]
