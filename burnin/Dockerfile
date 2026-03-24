FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-11 AS builder

WORKDIR /app

# Copy SDK and build it first
COPY kubemq-java/pom.xml /app/sdk/pom.xml
COPY kubemq-java/src /app/sdk/src
WORKDIR /app/sdk
RUN mvn install -DskipTests -q

# Copy burnin and build
COPY burnin/pom.xml /app/burnin/pom.xml
WORKDIR /app/burnin
RUN mvn dependency:go-offline -q
COPY burnin/src /app/burnin/src
RUN mvn package -DskipTests -q

FROM --platform=$TARGETPLATFORM eclipse-temurin:11-jre

RUN groupadd -r burnin && useradd -r -g burnin -s /bin/false burnin

WORKDIR /app
COPY --from=builder /app/burnin/target/burnin-*.jar /app/burnin.jar
COPY burnin/burnin-config.yaml /app/

USER burnin
EXPOSE 8888

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8888/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/burnin.jar"]
CMD ["--config", "/etc/burnin/config.yaml"]
