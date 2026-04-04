# ==========================================
# Construcción (Build)
# ==========================================
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Ejecución (Run)
# ==========================================
FROM amazoncorretto:21-alpine-jdk
WORKDIR /app

COPY --from=build /app/target/domesticas-api-0.0.1-SNAPSHOT.jar /api-v1.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/api-v1.jar"]
