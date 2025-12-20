# --- ETAPA 1: CONSTRUCCIÓN ---
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# --- ETAPA 2: EJECUCIÓN ---
FROM eclipse-temurin:17-jdk-alpine

# 1. Instalamos dependencias: Python, FFmpeg Y AHORA NODEJS
# Node.js es OBLIGATORIO para que yt-dlp resuelva los retos antibot de YouTube
RUN apk add --no-cache python3 ffmpeg nodejs

# 2. Instalamos yt-dlp
RUN wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp
RUN chmod a+rx /usr/local/bin/yt-dlp

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# MODO SUPERVIVENCIA (El mismo que ya funcionó)
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xss256k", "-XX:ReservedCodeCacheSize=64M", "-Xmx140m", "-Xms140m", "-jar", "app.jar"]