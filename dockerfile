FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

ENV MAVEN_OPTS="-Dmaven.resolver.transport=wagon -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"

COPY pom.xml .

RUN set -eux; \
    SETTINGS=""; \
    if [ -f /usr/share/maven/ref/settings.xml ]; then SETTINGS="-s /usr/share/maven/ref/settings.xml"; fi; \
    mvn $SETTINGS -q -DskipTests -U dependency:go-offline

COPY src ./src
RUN set -eux; \
    SETTINGS=""; \
    if [ -f /usr/share/maven/ref/settings.xml ]; then SETTINGS="-s /usr/share/maven/ref/settings.xml"; fi; \
    mvn $SETTINGS -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar

RUN set -eux; \
    groupadd -g 10001 app; \
    useradd -u 10001 -g 10001 -m -s /usr/sbin/nologin app; \
    chown -R 10001:10001 /app
USER 10001

EXPOSE 8888
ENTRYPOINT ["java","-jar","/app/app.jar"]