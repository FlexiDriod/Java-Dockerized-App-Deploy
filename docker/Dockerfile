# -----------------------------
# 1️⃣ Build stage
# -----------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
LABEL authors="Sudip"
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# -----------------------------
# 1️⃣ Runtime stage
# -----------------------------
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
