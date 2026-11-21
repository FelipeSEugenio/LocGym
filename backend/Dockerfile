# syntax=docker/dockerfile:1

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/locgym-backend-*.jar extract --layers --launcher --destination target/extraido

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 locgym && adduser -S -u 10001 -G locgym locgym
WORKDIR /app
COPY --from=build /app/target/extraido/dependencies/ ./
COPY --from=build /app/target/extraido/spring-boot-loader/ ./
COPY --from=build /app/target/extraido/snapshot-dependencies/ ./
COPY --from=build /app/target/extraido/application/ ./
ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
USER locgym
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
