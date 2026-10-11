# Stage 1: Build stage with Maven and Java 17
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy Maven descriptor and source code
COPY pom.xml .
COPY src ./src

# Package the application (skip tests during container build for faster deployment)
RUN mvn -B -DskipTests package

# Stage 2: Runtime stage with lightweight Java 17 JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the executable Spring Boot JAR from the build stage
# Actual JAR filename defined by pom.xml: <artifactId>-<version>.jar (yatra-ai-backend-0.0.1-SNAPSHOT.jar)
COPY --from=build /app/target/yatra-ai-backend-0.0.1-SNAPSHOT.jar app.jar

# Render dynamically sets the PORT environment variable (Spring Boot binds via server.port=${PORT:8080})
ENV PORT=8080
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
