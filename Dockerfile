FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline
COPY backend/src/main/java/ backend/src/main/java/
COPY backend/src/main/resources/ backend/src/main/resources/
RUN mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=backend /build/backend/target/card-chess.jar app.jar
COPY --from=frontend /build/frontend/dist/ frontend/dist/
COPY prompts/ prompts/
RUN mkdir -p /app/data
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
