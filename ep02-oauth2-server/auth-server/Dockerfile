ARG MAVEN_IMAGE=maven:3.10.2-eclipse-temurin-25-noble
ARG RUNTIME_IMAGE=eclipse-temurin:25-jre-noble

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace

COPY pom.xml ./
COPY auth-server/pom.xml auth-server/pom.xml
COPY consumer-service/pom.xml consumer-service/pom.xml
COPY management-service/pom.xml management-service/pom.xml
COPY auth-server/src auth-server/src

RUN mvn --batch-mode --no-transfer-progress \
    --projects auth-server \
    --also-make \
    -Dmaven.test.skip=true \
    package

FROM ${RUNTIME_IMAGE} AS runtime

RUN mkdir -p /app /home/app/.wallet-demo \
    && chown -R 10001:10001 /app /home/app/.wallet-demo

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/auth-server/target/auth-server-*.jar /app/app.jar

ENV HOME=/home/app
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.home=/home/app", "-jar", "/app/app.jar"]
