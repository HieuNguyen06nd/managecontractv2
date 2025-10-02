# ====== Stage 1: Build jar ======
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# copy các file để cache dependency
COPY pom.xml .
# Nếu có thư mục .mvn (wrapper/toolchains), copy luôn để Maven dùng đúng cấu hình:
COPY .mvn/ .mvn/
# kéo dependency để cache (tùy chọn, giúp build nhanh lần sau)
RUN mvn -B -q -DskipTests dependency:go-offline

# copy source và build
COPY src ./src
RUN mvn -B -DskipTests package

# ====== Stage 2: Runtime ======
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /build/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
