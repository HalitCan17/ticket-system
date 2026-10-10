# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
ENV MAVEN_OPTS="-Duser.language=en -Duser.country=US -Dfile.encoding=UTF-8"
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Tests run outside the image build (they need Docker for Testcontainers).
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && cp target/itsm-backend-*.jar /workspace/app.jar

FROM eclipse-temurin:21.0.12_8-jre-noble
# /data/attachments is owned by app so the (initially empty) attachments volume is writable.
RUN groupadd --system app && useradd --system --gid app --no-create-home app \
    && mkdir -p /data/attachments && chown app:app /data/attachments
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.language=en", "-Duser.country=US", "-jar", "/app/app.jar"]
