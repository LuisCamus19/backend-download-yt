# --- ETAPA 1: CONSTRUCCIÓN ---
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# --- ETAPA 2: EJECUCIÓN ---
FROM eclipse-temurin:17-jdk-alpine

# Instalamos dependencias
RUN apk add --no-cache python3 ffmpeg

# Instalamos yt-dlp
RUN wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp
RUN chmod a+rx /usr/local/bin/yt-dlp

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# COMANDO DE INICIO (MODO ULTRA-LIGERO)
# -Xmx140m: Heap máximo de 140MB (Muy ajustado)
# -Xss256k: Reduce el tamaño de la pila de hilos (Ahorra memoria nativa)
# -XX:ReservedCodeCacheSize=64M: Limita la caché de código JIT
# -XX:+UseSerialGC: El recolector más ligero posible
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xss256k", "-XX:ReservedCodeCacheSize=64M", "-Xmx140m", "-Xms140m", "-jar", "app.jar"]