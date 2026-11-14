# Gateway image.
# Build from the platform/ directory so the build context includes the
# shared library (the gateway depends on epitomejob-common):
#   docker build -f gateway-service/Dockerfile -t epitomejob/gateway-service:5a .
# The OTel Java agent is downloaded at build time (v2.18.0, verified on
# GitHub releases) and enabled via JAVA_TOOL_OPTIONS; traces go to the
# OTLP endpoint below (override with OTEL_EXPORTER_OTLP_ENDPOINT in compose).
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
# The gateway depends on epitomejob-common: install the library first,
# then build the gateway against it. (No aggregator pom is copied: a
# -pl build would fail because the sibling module POMs are absent here.)
COPY libs/epitomejob-common/pom.xml libs/epitomejob-common/
COPY libs/epitomejob-common/src libs/epitomejob-common/src
RUN mvn -q -f libs/epitomejob-common/pom.xml install -DskipTests
COPY gateway-service/pom.xml gateway-service/
COPY gateway-service/src gateway-service/src
RUN mvn -q -f gateway-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/gateway-service/target/gateway-service-*.jar app.jar
# OTel Java agent (v2.18.0, verified on GitHub releases), baked into the image.
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.18.0/opentelemetry-javaagent.jar /opt/otel/opentelemetry-javaagent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/otel/opentelemetry-javaagent.jar"
ENV OTEL_SERVICE_NAME=gateway-service
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
