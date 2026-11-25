# syntax=docker/dockerfile:1

# Stage 1: Build ClinicFlow JAR and runtime dependencies
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY clinicflow/pom.xml clinicflow/pom.xml
RUN mvn -f clinicflow/pom.xml -B dependency:go-offline
COPY clinicflow/src clinicflow/src
RUN mvn -f clinicflow/pom.xml -B package -DskipTests

# Stage 2: Lightweight JRE runtime for Google Cloud Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create non-root user and prepare writable runtime directories before dropping privileges
RUN useradd --create-home --shell /bin/bash clinicflow \
    && mkdir -p /app/data/processed /app/data/app /app/models /app/reports \
    && chown -R clinicflow:clinicflow /app

# Copy built artifact, runtime libraries, and compact seed data with proper ownership
COPY --from=build --chown=clinicflow:clinicflow /workspace/clinicflow/target/clinicflow-1.0.0-SNAPSHOT.jar /app/clinicflow.jar
COPY --from=build --chown=clinicflow:clinicflow /workspace/clinicflow/target/lib /app/lib
COPY --chown=clinicflow:clinicflow clinicflow/data /app/data

RUN chmod -R u+rwX /app/data /app/models /app/reports

USER clinicflow

ENV PORT=8080
ENV CLINICFLOW_HOME=/app
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xms64m", "-Xmx300m", "-Djava.awt.headless=true", "-jar", "/app/clinicflow.jar", "--server"]
