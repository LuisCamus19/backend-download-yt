# --- ETAPA 1: CONSTRUCCIÓN (BUILD) ---
# Usamos una imagen que ya tiene Maven y Java 17
FROM maven:3.8.5-openjdk-17 AS build

# Copiamos el código de tu proyecto a la carpeta /app de la imagen
WORKDIR /app
COPY . .

# Ejecutamos el comando para compilar (saltando los tests para ir más rápido)
RUN mvn clean package -DskipTests

# --- ETAPA 2: EJECUCIÓN (RUNTIME) ---
# Usamos una imagen ligera de Java 17 (Alpine Linux)
FROM eclipse-temurin:17-jdk-alpine

# 1. Instalamos las dependencias del sistema operativo (Linux)
# yt-dlp necesita Python3. ffmpeg lo necesitamos para unir video+audio.
RUN apk add --no-cache python3 ffmpeg

# 2. Instalamos yt-dlp manualmente (descargando la última versión oficial)
RUN wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp

# 3. Le damos permisos de ejecución a yt-dlp
RUN chmod a+rx /usr/local/bin/yt-dlp

# 4. Creamos la carpeta de la aplicación
WORKDIR /app

# 5. Copiamos SOLO el archivo .jar generado en la Etapa 1
# (Esto hace que la imagen final sea liviana)
COPY --from=build /app/target/*.jar app.jar

# 6. Exponemos el puerto 8080 (donde escucha Spring Boot)
EXPOSE 8080

# 7. Comando para iniciar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]