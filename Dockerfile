# 1. Build Stage
FROM dhi.io/amazoncorretto:25-alpine3.23 AS builder

# Set the working directory
WORKDIR /app

# Copy maven wrapper and POM
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Fetch dependencies (layer caching)
RUN ./mvnw dependency:go-offline

# Copy the actual source code
COPY src ./src

# Build the application, skipping tests for speed in image building (Tests will run in CI before this step)
RUN ./mvnw clean package -DskipTests

# 2. Runtime Stage
FROM dhi.io/amazoncorretto:25-alpine3.23

# Minimal distroless/alpine images may not have /bin/sh for running RUN commands.
# We skip creating the custom user for this specific image constraint.

WORKDIR /app

# Copy the built artifact from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the standard Spring Boot port 
EXPOSE 8080

# Environment variables matching our Terraform AWS setup
ENV SPRING_PROFILES_ACTIVE=prod
ENV AWS_REGION=us-east-1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
