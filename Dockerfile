# Multi-stage build: build fat jar then assemble runtime image based on Flink
# Stage 1: build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew /app/gradlew
RUN chmod +x /app/gradlew
COPY gradle /app/gradle
COPY build.gradle.kts settings.gradle.kts /app/
COPY src /app/src
# Build the shadow (fat) jar
RUN ./gradlew --no-daemon clean build

# Stage 2: runtime using official Flink image compatible with Kubernetes Operator
# Match Flink version with build.gradle.kts (1.20.x)
# Use Java 17 base image (official images for 1.20 are published for Java 17)
FROM flink:1.20.2

# Copy user jar into Flink expected directory
# The shadowJar name pattern: <project>-<version>-all.jar
ARG JAR_PATH=/app/build/libs
COPY --from=build ${JAR_PATH}/zm-scpro-disa-results-pipeline-*-all.jar /opt/flink/usrlib/app.jar

# Default command is provided by Flink image; the Operator will set the job parameters.
