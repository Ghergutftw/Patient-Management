#This has maven 3.9.9 and JDK 21
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .

# Download dependencies
# This will cache the dependencies layer, so it doesn't need to be redownloaded unless pom.xml changes
RUN mvn dependency:go-offline -B

# Copy the pom.xml and download dependencies first to leverage Docker cache
COPY src ./src

# Create the JAR file
RUN mvn clean package -DskipTests

FROM openjdk:21-jdk AS RUNNER

WORKDIR /app

COPY --from=builder /app/target/api-gateway-0.0.1-SNAPSHOT.jar ./app.jar

EXPOSE 4004