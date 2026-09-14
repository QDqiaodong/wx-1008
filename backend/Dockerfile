ARG DOCKER_REGISTRY=
FROM ${DOCKER_REGISTRY}maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml settings.xml ./
RUN mkdir -p /root/.m2     && cp settings.xml /root/.m2/settings.xml     && mvn -s settings.xml -B -q dependency:go-offline -DskipTests
COPY src ./src
RUN mvn -s settings.xml -B -q package -DskipTests

FROM ${DOCKER_REGISTRY}eclipse-temurin:17-jre
WORKDIR /app
ENV LANG=C.UTF-8     LC_ALL=C.UTF-8     JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
COPY --from=build /app/target/*.jar app.jar
EXPOSE 3308
ENTRYPOINT ["java", "-jar", "app.jar"]
