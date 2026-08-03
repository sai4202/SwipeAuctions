# Multi-stage build so the final image only carries the JRE + the built jar, not the whole Maven
# cache/toolchain. Backend only — the frontend now lives in its own repo (swipeauctions-frontend)
# and deploys separately on Vercel.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Layer cache: dependencies only re-download when pom.xml actually changes, not on every code edit.
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# Railway assigns the real port via $PORT (see application.yml's server.port) — EXPOSE here is
# documentation, not a binding.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
