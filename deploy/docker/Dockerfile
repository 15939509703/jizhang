FROM docker.1ms.run/library/maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY jizhang-common ./jizhang-common
COPY jizhang-security ./jizhang-security
COPY jizhang-user ./jizhang-user
COPY jizhang-bootstrap ./jizhang-bootstrap

RUN mvn -pl jizhang-bootstrap -am clean package -DskipTests

FROM docker.1ms.run/library/eclipse-temurin:21-jre
WORKDIR /app

ENV JAVA_OPTS="-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError"

COPY --from=build /workspace/jizhang-bootstrap/target/jizhang-bootstrap-1.0.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
