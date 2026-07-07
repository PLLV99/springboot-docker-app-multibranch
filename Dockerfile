# STAGE 1: Build Stage - ใช้ Maven Image เพื่อ Build โปรเจกต์
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# STAGE 2: Run Stage - ใช้ JRE Image ขนาดเล็กเพื่อ Run แอป
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]