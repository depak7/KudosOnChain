# Step 1: Use a lightweight OpenJDK base image
FROM eclipse-temurin:21-jdk-alpine

# Step 2: Set working directory
WORKDIR /app

# Step 3: Copy the JAR file (assuming you've built it already)
# Example: target/myapp-0.0.1-SNAPSHOT.jar
COPY target/*.jar app.jar

# Step 4: Expose the port your app runs on
EXPOSE 8080

# Step 5: Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
