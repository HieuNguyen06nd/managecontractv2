# ====== Stage 1: Build JAR ======
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy file cấu hình Maven để cache dependency
COPY pom.xml .
COPY .mvn/ .mvn/
RUN mvn -B -q -DskipTests dependency:go-offline

# Copy toàn bộ source
COPY src ./src

# Build JAR (bỏ test)
RUN mvn -B -DskipTests clean package

# ====== Stage 2: Runtime ======
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy JAR từ stage build sang stage runtime
COPY --from=build /build/target/*.jar app.jar

# Mở port ứng dụng
EXPOSE 8080

# Tùy chọn: đặt biến môi trường nếu cần
# ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]
