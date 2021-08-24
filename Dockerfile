FROM gradle:jdk11 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon
FROM openjdk:11
COPY --from=build /home/gradle/src/build/libs/*.jar /app/kakaowork-bot.jar
CMD ["java", "-jar", "/app/kakaowork-bot.jar"]