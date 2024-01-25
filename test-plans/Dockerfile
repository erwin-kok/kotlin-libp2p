# syntax=docker/dockerfile:1
FROM gradle:jdk17 AS BUILD
COPY --chown=gradle:gradle . /home/gradle
WORKDIR /home/gradle
RUN gradle build --no-daemon -x test -x detekt -x koverVerify -x koverHtmlReport

FROM eclipse-temurin:17-jdk-jammy
RUN mkdir /app
COPY --from=BUILD /home/gradle/test-plans/build/libs/test-plans-shadow.jar /app/application.jar
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom","-jar","/app/application.jar"]
