package pe.edu.upc.backdownloadmusicmp3.servicesimplements;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import pe.edu.upc.backdownloadmusicmp3.dtos.AudioResponse;
import pe.edu.upc.backdownloadmusicmp3.servicesinterfaces.IAudioDownloadService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor // Inyecta automáticamente el repositorio en el constructor
public class AudioDownloadServiceImplement implements IAudioDownloadService {

    @Value("${app.tools.ytdlp}")
    private String ytDlpPath;

    @Value("${app.tools.ffmpeg}")
    private String ffmpegDir;

    @Override
    public AudioResponse downloadAudio(String videoUrl, String quality) throws IOException {
        return ejecutarDescarga(videoUrl, quality, "mp3");
    }

    @Override
    public AudioResponse downloadVideo(String videoUrl, String qualityResolution) throws IOException {
        return ejecutarDescarga(videoUrl, qualityResolution, "mp4");
    }

    private AudioResponse ejecutarDescarga(String videoUrl, String qualityParam, String format) throws IOException {

        System.out.println("--- INICIANDO DESCARGA ---");
        System.out.println("URL: " + videoUrl);

        // 1. SIN OBTENER TÍTULO PREVIO (AHORRO RAM)
        String videoTitle = "musica_descargada";

        // 2. CONFIGURACIÓN DE RUTAS
        String uniqueFileName = "temp_" + UUID.randomUUID().toString() + "." + format;
        String systemTempDir = System.getProperty("java.io.tmpdir");
        Path tempFilePath = Paths.get(systemTempDir, uniqueFileName);

        // 3. CONSTRUCCIÓN DEL COMANDO
        List<String> commands = new ArrayList<>();
        commands.add(ytDlpPath);
        commands.add("--ffmpeg-location");
        commands.add(ffmpegDir);
        commands.add("--force-ipv4");
        commands.add("--no-playlist");
        commands.add("-o");
        commands.add(tempFilePath.toString());
        commands.add("--force-overwrites");

        // --- OPTIMIZACIÓN DE MEMORIA ---
        commands.add("--buffer-size");
        commands.add("1024");

        // --- DISFRAZ ANTI-BOT (User-Agent de Chrome en Windows) ---
        commands.add("--user-agent");
        commands.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Intento extra para evitar bloqueos geográficos
        commands.add("--geo-bypass");

        if ("mp3".equals(format)) {
            // --- LOGICA MP3 ---
            String bitrate = (qualityParam == null) ? "128K" : qualityParam + "K";
            if (!bitrate.matches("\\d+K")) bitrate = "128K";

            commands.add("-x");
            commands.add("--audio-format");
            commands.add("mp3");
            commands.add("--audio-quality");
            commands.add(bitrate);
            commands.add("--add-metadata");
        } else {
            // --- LOGICA MP4 (Modo Ligero) ---
            String res = (qualityParam == null) ? "720" : qualityParam;
            commands.add("-f");
            // Pedimos el mejor mp4 pre-unido para evitar que FFmpeg explote la RAM
            commands.add("best[ext=mp4][height<=" + res + "]/best[ext=mp4]/best");
        }

        commands.add(videoUrl);

        // 4. EJECUCIÓN DEL PROCESO
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        // Redirigimos errores al flujo normal para poder leerlos en los logs de Render
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();

            // LEER LOGS EN TIEMPO REAL (Para depuración)
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[yt-dlp]: " + line);
                }
            }

            int exitCode = process.waitFor();
            System.out.println("Código de salida: " + exitCode);

            if (exitCode != 0) {
                throw new IOException("yt-dlp falló con código " + exitCode + ". Revisa los logs anteriores.");
            }

            if (!Files.exists(tempFilePath)) {
                throw new IOException("El archivo temporal no se generó en: " + tempFilePath);
            }

            // Leer archivo y borrarlo del disco
            byte[] fileContent = Files.readAllBytes(tempFilePath);
            Files.delete(tempFilePath);

            return new AudioResponse(
                    new ByteArrayResource(fileContent),
                    videoTitle + "." + format
            );

        } catch (Exception e) {
            System.err.println("!!! ERROR CRÍTICO EN DESCARGA !!!");
            e.printStackTrace();
            throw new IOException("Error interno procesando descarga", e);
        }
    }

}
