# ---- build stage: compile and package with Maven (tests run in CI, not in the image build) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- run stage: small JRE image, non-root user ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home app && mkdir -p /data/uploads && chown -R app /data
USER app
WORKDIR /app
COPY --from=build /src/target/jansunwai-*.jar app.jar
ENV UPLOAD_DIR=/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
