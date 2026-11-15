# Imágenes fijadas por digest (Temurin 25.0.5+8 sobre Ubuntu Noble, consultadas el 2026-11-15).
FROM eclipse-temurin:25.0.5_8-jdk-noble@sha256:80f67fb3d6f76e2d817d98a4cc2a9cf05f054d4e6adc5e2144bde671d722e63f AS construccion
WORKDIR /fuente
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B -DskipTests package && cp target/perfil-api-*.jar app.jar

FROM eclipse-temurin:25.0.5_8-jre-noble@sha256:e3b6f7887a50f20d8f07abf279d8a81a5953fb398a9555bd9edd56b9bd15968c
RUN useradd --system --uid 10001 --no-create-home perfil
WORKDIR /app
COPY --from=construccion /fuente/app.jar app.jar
USER 10001
# Tope de memoria: el servidor es compartido. Coolify aplica además el límite del contenedor (512 MB).
# Subió de 192 a 224 MB de heap y de 128 a 160 MB de metaspace con la base de datos, las migraciones y el correo.
ENV JAVA_TOOL_OPTIONS="-Xmx224m -Xss512k -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
