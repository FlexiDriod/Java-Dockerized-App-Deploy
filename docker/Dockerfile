# -----------------------------
# 1️⃣ Build stage (Java 21 + Maven)
# -----------------------------
FROM maven:3.9.3-temurin-21-slim AS build
LABEL authors="Sudip"

WORKDIR /build

# Copy pom.xml first (cache dependencies)
COPY pom.xml ./
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build Spring Boot JAR
RUN mvn clean package -DskipTests

# -----------------------------
# 2️⃣ Runtime stage (Java 21 JRE)
# -----------------------------
FROM eclipse-temurin:21-jre-alpine
LABEL authors="Sudip"

# Create non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

# Copy the built JAR
COPY --from=build /build/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Run Spring Boot app
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
