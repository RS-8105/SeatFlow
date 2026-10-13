# ==========================================
# Stage 1: Build the Spring Boot application
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy pom.xml first to take advantage of Docker layer caching for dependencies
COPY pom.xml .

# Pre-fetch project dependencies in batch mode
RUN mvn dependency:go-offline -B

# Copy the application source code
COPY src ./src

# Package the runnable fat JAR (skipping tests during image assembly)
RUN mvn clean package -DskipTests -B

# ==========================================
# Stage 2: Production runtime image
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Create an unprivileged user and group to run the application securely
RUN addgroup -S -g 1001 appgroup && \
    adduser -S -u 1001 -G appgroup appuser

# Copy the repackaged executable Spring Boot JAR from the builder stage
# (Maven builds ticket-booking-backend-1.0.0-SNAPSHOT.jar based on pom.xml artifactId & version)
COPY --from=builder --chown=appuser:appgroup /build/target/ticket-booking-backend-*.jar app.jar

# Run as non-root user
USER appuser:appgroup

# Default listening port (overrideable via PORT environment variable)
ENV PORT=8080
EXPOSE 8080

# Configure JVM flags and execute Spring Boot application binding to the provided PORT
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --server.port=${PORT}"]
