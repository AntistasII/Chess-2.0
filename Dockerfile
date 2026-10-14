FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-jammy AS backend
WORKDIR /build
COPY backend/src/main/java/ backend/src/main/java/
RUN mkdir -p classes && find backend/src/main/java -name '*.java' > sources.list \
    && javac --release 21 -encoding UTF-8 -d classes @sources.list

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=backend /build/classes/ backend/out/
COPY --from=frontend /build/frontend/dist/ frontend/dist/
COPY prompts/ prompts/
RUN mkdir -p /app/data
EXPOSE 8080
CMD ["java", "-cp", "backend/out", "chess.server.Server"]
