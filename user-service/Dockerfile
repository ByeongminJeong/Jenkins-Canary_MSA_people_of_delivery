FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

COPY gradlew ./gradlew
RUN chmod +x ./gradlew
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY module-common/ ./module-common
COPY user-service/ ./user-service

RUN ./gradlew :user-service:bootJar --no-daemon --build-cache

# 실행 단계
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/user-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]


