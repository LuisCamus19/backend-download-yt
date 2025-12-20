# --- ETAPA 1: CONSTRUCCIÓN (BUILD) ---
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
# Compilamos saltando tests
RUN mvn clean package -DskipTests

# --- ETAPA 2: EJECUCIÓN (RUNTIME) ---
FROM eclipse-temurin:17-jdk-alpine

# 1. Instalamos dependencias
RUN apk add --no-cache python3 ffmpeg

# 2. Instalamos yt-dlp
RUN wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp
RUN chmod a+rx /usr/local/bin/yt-dlp

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# 3. COMANDO DE INICIO (MODO SUPERVIVENCIA EXTREMA)
# -XX:+UseSerialGC: Usa un recolector de basura simple que gasta MUCHA menos RAM.
# -Xss512k: Reduce el tamaño de cada hilo a la mitad (ahorra RAM).
# -Xmx180m: Limitamos Java a solo 180MB. Es arriesgado, pero necesario.
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xss512k", "-Xmx180m", "-Xms180m", "-jar", "app.jar"]