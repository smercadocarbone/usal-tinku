FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline || true
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Fail-closed (AUD-004): sin override explícito la imagen arranca en `prod`, que exige
# config real y rechaza el OCR stub. docker-compose.yml pasa `dev` para el stack local.
ENV SPRING_PROFILES_ACTIVE=prod
CMD ["java", "-jar", "app.jar"]