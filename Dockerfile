# Digital Nepal Ecosystem - Backend Dockerfile
# Multi-stage build for Java 21 + Spring Boot 3.x
# Build stage optimized for CI/CD pipeline

# ============================================================================
# STAGE 1: BUILD
# ============================================================================
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy project files
COPY .mvn .mvn
COPY mvnw mvnw
COPY pom.xml .
COPY modules/ modules/
COPY db/ db/

# The bootstrap module is the canonical runnable Spring Boot application. It
# includes every business module and the shared API documentation resources.

# Ensure wrapper is executable and run a full multi-module build
RUN chmod +x mvnw || true
# Install curl/unzip so the mvnw script can download the maven-wrapper.jar and Maven distro
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip ca-certificates && rm -rf /var/lib/apt/lists/*

# Use the wrapper to build the project
RUN ./mvnw -B -DskipTests clean package -pl modules/bootstrap -am

# Target the explicitly named production binary—no wildcards, no guessing!
RUN mkdir -p /build-output && \
    cp modules/bootstrap/target/digital-nepal-ecosystem.jar /build-output/app.jar

# ============================================================================
# STAGE 2: RUNTIME
# ============================================================================
FROM eclipse-temurin:21-jre

LABEL maintainer="Digital Nepal DevOps <devops@digital-nepal.gov.np>"
LABEL description="Digital Nepal Citizen Ecosystem Backend Service"
LABEL version="1.0"

WORKDIR /opt/digital-nepal

# Install curl for health checks, plus Tesseract OCR (+ English and Nepali
# trained data) for NidDocumentScanService's citizenship-certificate scanning.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        tesseract-ocr \
        tesseract-ocr-eng \
        tesseract-ocr-nep && \
    rm -rf /var/lib/apt/lists/*

# Copy compiled JAR from builder
COPY --from=builder /build-output/app.jar /opt/digital-nepal/app.jar

# Create non-root user for security
RUN useradd -r -u 1500 -g 1000 --no-create-home appuser && \
    chown -R 1500:1000 /opt/digital-nepal

USER appuser

CMD ["java", "-jar", "/opt/digital-nepal/app.jar"]
