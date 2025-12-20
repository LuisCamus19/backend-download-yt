# --- ETAPA 1: CONSTRUCCIÓN (BUILD) ---
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
# Compilamos saltando tests
RUN mvn clean package -DskipTests

# --- ETAPA 2: EJECUCIÓN (RUNTIME) ---
FROM eclipse-temurin:17-jdk-alpine

# 1. Instalamos dependencias de Linux (Python y FFmpeg)
RUN apk add --no-cache python3 ffmpeg

# 2. Instalamos yt-dlp manualmente
RUN wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp
RUN chmod a+rx /usr/local/bin/yt-dlp

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# 3. COMANDO DE INICIO (OPTIMIZADO)
# -Xmx220m: Limitamos Java a 220MB de RAM.
# Esto deja casi 300MB libres (de los 512MB de Render) para que yt-dlp y el sistema respiren.
ENTRYPOINT ["java", "-Xmx220m", "-Xms220m", "-jar", "app.jar"]