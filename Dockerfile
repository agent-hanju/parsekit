FROM gradle:8.14-jdk21 AS builder

WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle ./

# Copy source code
COPY src ./src

# Build the application
RUN gradle build --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

# Install LibreOffice with H2Orestart extension for HWP support, Poppler for PDF to image, curl for healthcheck, and create non-root user
RUN apt-get update && apt-get install -y --no-install-recommends \
    libreoffice \
    libreoffice-java-common \
    fonts-unfonts-core \
    poppler-utils \
    curl \
    wget \
    && wget -q -O /tmp/H2Orestart.oxt \
        "https://github.com/ebandal/H2Orestart/releases/download/v0.7.9/H2Orestart.oxt" \
    && unopkg add --shared /tmp/H2Orestart.oxt \
    && rm /tmp/H2Orestart.oxt \
    && apt-get purge -y wget \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r parsekit && useradd -r -g parsekit parsekit

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Set ownership
RUN chown -R parsekit:parsekit /app

USER parsekit

EXPOSE 8000

# Run the Spring Boot application with JAVA_OPTS support
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
