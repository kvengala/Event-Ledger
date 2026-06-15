FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY account-service/pom.xml account-service/
COPY event-gateway/pom.xml event-gateway/

RUN mvn -B -pl event-gateway -am dependency:go-offline -DskipTests

COPY account-service account-service
COPY event-gateway event-gateway
RUN mvn -B -pl event-gateway -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/event-gateway/target/event-gateway-*.jar app.jar

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
