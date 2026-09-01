# syntax=docker/dockerfile:1.6

# Build stage — uses maintained Maven + Eclipse Temurin (no more deleted openjdk:*)
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY api/pom.xml ./pom.xml
COPY api/mvnw ./mvnw
COPY api/mvnw.cmd ./mvnw.cmd
COPY api/.mvn ./.mvn
COPY api/src ./src
RUN mvn -DskipTests -q clean package

# Runtime stage — maintained Eclipse Temurin 17 JRE on Ubuntu 22.04 slim
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        git \
        openssh-client \
        ca-certificates \
        tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /root/.ssh \
    && chmod 700 /root/.ssh \
    && printf "Host *\n  StrictHostKeyChecking accept-new\n  UserKnownHostsFile /dev/null\n" > /root/.ssh/config \
    && chmod 600 /root/.ssh/config
ENV SECURE_COOKIES=true
COPY --from=builder /app/target/api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]