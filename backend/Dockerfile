FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY backend ./backend
RUN mkdir unimock
RUN chmod +x gradlew && ./gradlew :backend:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S hotelopai && adduser -S hotelopai -G hotelopai
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/backend-*.jar /app/hotelopai.jar
USER hotelopai
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/hotelopai.jar"]
