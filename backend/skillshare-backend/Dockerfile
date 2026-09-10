# Multi-stage build so the final image only ships a JRE + the built jar,
# not the full JDK + Maven + downloaded dependencies (keeps the image
# small, which matters for free-tier build/deploy speed on Render).
#
# Root Directory for this service on Render must be set to
# backend/skillshare-backend so this Dockerfile's build context is that
# folder (pom.xml, mvnw, src/, etc.), not the whole monorepo.

# --- Build stage ---
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy just the wrapper + pom first so dependency resolution is cached in
# its own Docker layer and doesn't re-run on every source change.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/skillshare-backend-0.0.1-SNAPSHOT.jar app.jar

# Actual bound port comes from Spring reading Render's $PORT env var at
# runtime (see application-prod.yml) — this EXPOSE is documentation only.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
