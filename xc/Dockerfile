FROM eclipse-temurin:21-jre

ARG MODULE
WORKDIR /app
COPY ${MODULE}/target/*.jar /app/app.jar
ENV TZ=Asia/Shanghai

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
