FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY unimock ./unimock
RUN mkdir backend
RUN chmod +x gradlew && ./gradlew :unimock:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S unimock && adduser -S unimock -G unimock
WORKDIR /app
COPY --from=build /workspace/unimock/build/libs/unimock-*.jar /app/unimock.jar
USER unimock
EXPOSE 8090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/unimock.jar"]
