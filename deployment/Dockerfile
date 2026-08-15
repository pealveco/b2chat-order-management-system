FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon :app-service:bootJar

FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /workspace/applications/app-service/build/libs/*.jar OrderManagementSystem.jar
ENV JAVA_OPTS=" -XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
USER appuser
ENTRYPOINT [ "/bin/sh", "-c", "/opt/java/openjdk/bin/java $JAVA_OPTS  -jar /OrderManagementSystem.jar" ]
