# ===== Этап 1: Сборка =====
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Копируем файлы Maven
COPY pom.xml .

# Скачиваем зависимости
RUN mvn dependency:go-offline -B

# Копируем исходный код
COPY src src

# Собираем JAR
RUN mvn package -DskipTests -B

# ===== Этап 2: Запуск =====
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]