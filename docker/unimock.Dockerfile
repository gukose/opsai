FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY backend ./backend
COPY unimock ./unimock

RUN ./gradlew --no-daemon :unimock:bootJar && \
    find unimock/build/libs -maxdepth 1 -type f -name "*.jar" ! -name "*-plain.jar" -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8090
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
