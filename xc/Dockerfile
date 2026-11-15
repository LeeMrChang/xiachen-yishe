ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JAVA_IMAGE=eclipse-temurin:21-jre
FROM ${MAVEN_IMAGE} AS build
ARG MODULE
ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2/
ENV MAVEN_OPTS="-Xmx384m -XX:MaxMetaspaceSize=192m" \
    MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}
WORKDIR /build
COPY pom.xml maven-settings.xml ./
COPY xc-common/pom.xml xc-common/pom.xml
COPY xc-domain/pom.xml xc-domain/pom.xml
COPY xc-api/pom.xml xc-api/pom.xml
COPY xc-admin/pom.xml xc-admin/pom.xml
RUN mvn -B -ntp -s maven-settings.xml -pl ${MODULE} -am -DexcludeReactor=true dependency:go-offline
COPY xc-common/src/main xc-common/src/main
COPY xc-domain/src/main xc-domain/src/main
COPY xc-api/src/main xc-api/src/main
COPY xc-admin/src/main xc-admin/src/main
RUN mvn -B -ntp -s maven-settings.xml -pl ${MODULE} -am -DskipTests package

FROM ${JAVA_IMAGE}
ARG MODULE
ARG REVISION
LABEL org.opencontainers.image.revision=${REVISION}
WORKDIR /app
COPY --from=build /build/${MODULE}/target/*.jar /app/app.jar
ENV TZ=Asia/Shanghai

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
