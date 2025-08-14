FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy and resolve dependencies first (better layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Use JRE instead of JDK for smaller runtime image
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Copy the built jar
COPY --from=builder /app/target/auth-service-0.0.1-SNAPSHOT.jar ./app.jar

# Add a non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

USER appuser

EXPOSE 4003

# Use exec form for better signal handling
ENTRYPOINT ["java", "-jar", "app.jar"]