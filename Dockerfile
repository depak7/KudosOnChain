# -------------------------------------------
# 🏗️ Stage 1: Build the JAR using Maven
# -------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml first (for dependency caching)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies first (cached if pom.xml unchanged)
RUN ./mvnw dependency:go-offline -B

# Copy the rest of the source code
COPY src src

# Build the application JAR
RUN ./mvnw clean package -DskipTests

# -------------------------------------------
# 🚀 Stage 2: Run the JAR on a smaller image
# -------------------------------------------
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose app port (change if your app runs on a different one)
EXPOSE 8000

# Run the Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
