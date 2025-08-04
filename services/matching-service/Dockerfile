# Multi-stage build for Matching Service
FROM gradle:7.6-jdk17 AS builder

WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Download dependencies
RUN gradle dependencies --no-daemon

# Copy source code
COPY src ./src

# Build application
RUN gradle bootJar --no-daemon

# Runtime stage
FROM openjdk:17-jdk-slim

WORKDIR /app

# Install utilities for debugging
RUN apt-get update && apt-get install -y \
    curl \
    netcat \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy jar from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Copy wait script for dependencies
COPY --chown=appuser:appuser docker/wait-for-it.sh /wait-for-it.sh
RUN chmod +x /wait-for-it.sh

# Create directory for logs
RUN mkdir -p /app/logs && chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

# JVM options for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC"

# Entry point with dependency waiting
ENTRYPOINT ["/wait-for-it.sh", "postgres-test:5432", "--", \
            "/wait-for-it.sh", "redis-test:6379", "--", \
            "/wait-for-it.sh", "kafka-test:29093", "--", \
            "java", "-jar", "app.jar"]