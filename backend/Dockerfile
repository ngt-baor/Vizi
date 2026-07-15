FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod \
    APP_UPLOAD_IMAGE_DIR=/app/uploads/images

RUN addgroup -S vizi && adduser -S vizi -G vizi \
    && mkdir -p /app/uploads/images \
    && chown -R vizi:vizi /app

COPY --from=build /workspace/target/*.jar /app/app.jar

USER vizi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
