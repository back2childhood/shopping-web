FROM eclipse-temurin:17-jdk-alpine as builder
WORKDIR /app

COPY . .

RUN ./mvnw -Dmaven.test.skip=true clean package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]