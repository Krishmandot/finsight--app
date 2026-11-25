# Build stage: compiles the Vaadin production bundle (Node is downloaded by the Vaadin plugin)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -Pproduction -DskipTests package

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S finsight && adduser -S finsight -G finsight
COPY --from=build /app/target/*.jar app.jar
USER finsight
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

# If DB_URL is not set but DB_HOST is (as render.yaml does), build the JDBC URL from the parts.
# PORT is provided by the host (Render sets it); the app reads it from application.yml.
# SerialGC + 70% heap keeps memory low enough for small (512 MB) instances.
ENTRYPOINT ["sh", "-c", "if [ -z \"$DB_URL\" ] && [ -n \"$DB_HOST\" ]; then export DB_URL=\"jdbc:postgresql://${DB_HOST}:${DB_PORT:-5432}/${DB_NAME}\"; fi; exec java -XX:MaxRAMPercentage=70 -XX:+UseSerialGC -jar app.jar"]
