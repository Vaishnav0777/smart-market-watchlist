# Builds the API jar, then runs it on a JRE. PostgreSQL is external.
# Tests are skipped here; run ./mvnw test before building the image.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
COPY src src
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=build /src/target/smartwatch-*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
