FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY server/pom.xml server/pom.xml
RUN mvn -f server/pom.xml dependency:go-offline -B
COPY server/src server/src
RUN mvn -f server/pom.xml clean package -DskipTests -B

FROM eclipse-temurin:21-jre

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod \
    PORT=8080

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/storage /app/uploads \
    && useradd --system --uid 10001 --home-dir /app labportal
COPY --from=build /workspace/server/target/*.jar /app/application.jar
RUN chown -R labportal:labportal /app

USER labportal
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
