FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle main.gradle ./
COPY gradle ./gradle
COPY applications/app-service/build.gradle applications/app-service/build.gradle
COPY domain/model/build.gradle domain/model/build.gradle
COPY domain/usecase/build.gradle domain/usecase/build.gradle
COPY infrastructure/driven-adapters/notification/build.gradle infrastructure/driven-adapters/notification/build.gradle
COPY infrastructure/driven-adapters/r2dbc-postgresql/build.gradle infrastructure/driven-adapters/r2dbc-postgresql/build.gradle
COPY infrastructure/driven-adapters/redis/build.gradle infrastructure/driven-adapters/redis/build.gradle
COPY infrastructure/entry-points/reactive-web/build.gradle infrastructure/entry-points/reactive-web/build.gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet :app-service:dependencies --configuration runtimeClasspath > /dev/null

COPY . .
RUN ./gradlew --no-daemon :app-service:bootJar

FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
RUN apk add --no-cache curl && addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /workspace/applications/app-service/build/libs/*.jar OrderManagementSystem.jar
ENV JAVA_OPTS=" -XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
USER appuser
HEALTHCHECK --interval=10s --timeout=5s --retries=10 --start-period=30s \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'
ENTRYPOINT [ "/bin/sh", "-c", "/opt/java/openjdk/bin/java $JAVA_OPTS  -jar /OrderManagementSystem.jar" ]
