# EpitomeJob resume-builder-service — RB1.
# Multi-stage: Maven build, then a slim JRE runtime with curl for the
# /actuator/health healthcheck. Java 21 (pom.xml: java.version=21).
# Mirrors backend/Dockerfile (same base tags, same OTel javaagent v2.18.0,
# same ENTRYPOINT flag). The service is standalone — no shared-library
# dependency — so the build context is just this directory:
#   docker build -t epitomejob/resume-builder-service:rb1 ./resume-builder-service

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
# Spring Boot Maven plugin produces target/resume-builder-service-0.1.0.jar
# (artifactId resume-builder-service, version 0.1.0 in pom.xml); the glob
# keeps the Dockerfile valid if the version is bumped.
COPY --from=build /app/target/*.jar app.jar
# OTel Java agent (v2.18.0, same as backend/gateway) — the Micrometer OTel
# bridge has no OTLP exporter on the classpath, so without this agent no
# span would ever reach Jaeger. The agent honours OTEL_EXPORTER_OTLP_ENDPOINT
# + OTEL_SERVICE_NAME from the compose/k8s env.
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.18.0/opentelemetry-javaagent.jar /opt/otel/opentelemetry-javaagent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/otel/opentelemetry-javaagent.jar"
EXPOSE 8082
ENTRYPOINT ["java", "-Dnetworkaddress.cache.ttl=5", "-jar", "app.jar"]
