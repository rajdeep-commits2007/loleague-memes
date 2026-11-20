# syntax=docker/dockerfile:1
# Build context: ./backend   (docker build -f backend/auth-service/Dockerfile backend)
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/
COPY meme-service/pom.xml meme-service/
COPY chat-service/pom.xml chat-service/
# Warm the dependency cache (best effort; the sibling 'common' module isn't built yet)
RUN mvn -q -B -pl auth-service -am dependency:go-offline -DskipTests || true
COPY common common
COPY auth-service auth-service
RUN mvn -q -B -pl auth-service -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /src/auth-service/target/app.jar app.jar
USER app
# Tuned for small (512 MB) containers such as Render's starter plan
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
