# Eureka server image.
# Build from the platform/ directory so the build context is the aggregator:
#   docker build -f eureka-server/Dockerfile -t epitomejob/eureka-server:5a .
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY eureka-server/pom.xml eureka-server/
COPY eureka-server/src eureka-server/src
RUN mvn -q -f eureka-server/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/eureka-server/target/eureka-server-*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
