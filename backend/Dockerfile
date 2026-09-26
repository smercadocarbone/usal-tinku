FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline || true
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
# OCR real (ADR-M1-01, ADR-M1-08): el backend ejecuta el programa `tesseract` con los idiomas
# español (datos), inglés (zona MRZ del dorso) y osd (orientación); -eng y -osd vienen como
# dependencias de tesseract-ocr. Sin esto, en `prod` el registro con DNI responde "lector no
# disponible" (el stub está prohibido, AUD-034).
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-spa \
    && rm -rf /var/lib/apt/lists/* \
    && ln -s "$(dirname "$(find /usr/share/tesseract-ocr -name spa.traineddata | head -1)")" /opt/tessdata
ENV TESSDATA_PREFIX=/opt/tessdata
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Archivos subidos (fotos, credenciales, evidencia): en producción es un volumen (ADR-M1-03).
RUN mkdir -p /data && chown -R 1000:1000 /data /app
ENV ALMACENAMIENTO_DIRECTORIO=/data
USER 1000
EXPOSE 8080
# Fail-closed (AUD-004): sin override explícito la imagen arranca en `prod`, que exige
# config real y rechaza el OCR stub. docker-compose.yml pasa `dev` para el stack local.
ENV SPRING_PROFILES_ACTIVE=prod
# T13 A3: la JVM respeta el límite de memoria del contenedor y muere (y Coolify la
# reinicia) ante un OutOfMemoryError en vez de quedar zombi.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS -o /dev/null http://127.0.0.1:8080/actuator/health || exit 1
CMD ["java", "-jar", "app.jar"]
