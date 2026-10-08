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
FROM eclipse-temurin:25-jre

WORKDIR /app

# Системный пользователь без пароля, домашнего каталога и оболочки для входа
RUN useradd --system --no-create-home --shell /usr/sbin/nologin app

COPY --from=build --chown=app:app /app/target/*.jar app.jar

EXPOSE 8080

# Приложение запускается не от root
USER app

CMD ["java", "-jar", "app.jar"]