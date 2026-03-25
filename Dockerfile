# Шаг 1: Берём базовый образ — Linux с Java 21
FROM eclipse-temurin:21-jre

# Шаг 2: Копируем .jar файл внутрь образа
COPY target/studynotes-0.0.1-SNAPSHOT.jar app.jar

# Шаг 3: Указываем, что приложение слушает порт 8080
EXPOSE 8080

# Шаг 4: Команда запуска — что выполнить при старте контейнера
CMD ["java", "-jar", "app.jar"]