# Build stage — uses the official Gradle image so no local Gradle installation is needed.
FROM gradle:8.11-jdk21-alpine AS build
WORKDIR /app

# Copy dependency descriptors first to exploit Docker layer caching.
# The dependency layer is rebuilt only when build.gradle.kts or settings change.
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon --quiet 2>/dev/null || true

# Copy source and produce the executable fat-jar.
COPY src ./src
RUN gradle bootJar --no-daemon

# Runtime stage — minimal JRE image.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
