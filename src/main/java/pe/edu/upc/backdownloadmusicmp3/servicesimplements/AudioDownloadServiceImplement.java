package pe.edu.upc.backdownloadmusicmp3.servicesimplements;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import pe.edu.upc.backdownloadmusicmp3.dtos.AudioResponse;
import pe.edu.upc.backdownloadmusicmp3.entities.DownloadHistory;
import pe.edu.upc.backdownloadmusicmp3.repositories.DownloadHistoryRepository;
import pe.edu.upc.backdownloadmusicmp3.servicesinterfaces.IAudioDownloadService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor // Inyecta automáticamente el repositorio en el constructor
public class AudioDownloadServiceImplement implements IAudioDownloadService {

    private final DownloadHistoryRepository historyRepository;

    // --- INYECCIÓN DE RUTAS (FLEXIBILIDAD WINDOWS/LINUX) ---

    // Ruta completa al ejecutable de yt-dlp (ej: C:/.../yt-dlp.exe)
    @Value("${app.tools.ytdlp}")
    private String ytDlpPath;

    // Directorio donde está ffmpeg (ej: C:/.../bin/)
    @Value("${app.tools.ffmpeg}")
    private String ffmpegDir;

    // --- MÉTODOS PÚBLICOS ---

    @Override
    public AudioResponse downloadAudio(String videoUrl, String quality) throws IOException {
        // MP3: Pasamos el formato y la calidad (bitrate)
        return ejecutarDescarga(videoUrl, quality, "mp3");
    }

    @Override
    public AudioResponse downloadVideo(String videoUrl, String qualityResolution) throws IOException {
        // MP4: Pasamos el formato y la calidad (resolución)
        return ejecutarDescarga(videoUrl, qualityResolution, "mp4");
    }

    // --- LÓGICA CENTRAL UNIFICADA ---
    private AudioResponse ejecutarDescarga(String videoUrl, String qualityParam, String format) throws IOException {

        // 1. VALIDACIONES
        String youtubeRegex = "^(https?://)?(www\\.|m\\.)?(youtube\\.com|youtu\\.be)/.+$";
        if (videoUrl == null || !videoUrl.matches(youtubeRegex)) {
            throw new IllegalArgumentException("La URL proporcionada no es válida.");
        }

        // 2. OBTENER TÍTULO (Modo Seguro con Timeout)
        String videoTitle = "Archivo_Descargado";
        try {
            ProcessBuilder titlePb = new ProcessBuilder(
                    ytDlpPath, // Usamos la variable inyectada
                    "--get-title",
                    "--force-ipv4",
                    "--no-warnings",
                    "--no-playlist", // Vital para listas de reproducción
                    "--socket-timeout", "5",
                    videoUrl
            );

            // Descartamos errores para no ensuciar logs, leemos el output
            titlePb.redirectError(ProcessBuilder.Redirect.DISCARD);

            Process p = titlePb.start();

            // Esperamos máximo 10 segundos
            if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                videoTitle = new String(p.getInputStream().readAllBytes()).trim();
            } else {
                if (p.isAlive()) p.destroyForcibly(); // Matar si se cuelga
            }
        } catch (Exception e) {
            // Si falla el título, seguimos con el nombre por defecto
            System.err.println("Advertencia: No se pudo obtener el título: " + e.getMessage());
        }

        // Limpieza de caracteres prohibidos en nombres de archivo
        videoTitle = videoTitle.replaceAll("[\\\\/:*?\"<>|]", "");
        if (videoTitle.isEmpty()) videoTitle = "media_file";

        // 3. CONFIGURAR RUTAS TEMPORALES
        String uniqueFileName = "temp_" + UUID.randomUUID().toString() + "." + format;
        // Guardamos el temporal en la misma carpeta de ffmpeg (o podrías usar /tmp del sistema)
        Path tempFilePath = Paths.get(ffmpegDir, uniqueFileName);

        // 4. CONSTRUIR COMANDO DE DESCARGA
        List<String> commands = new ArrayList<>();
        commands.add(ytDlpPath);            // Ejecutable yt-dlp
        commands.add("--ffmpeg-location");
        commands.add(ffmpegDir);            // Carpeta ffmpeg
        commands.add("--force-ipv4");
        commands.add("--no-playlist");
        commands.add("-o");
        commands.add(tempFilePath.toString());
        commands.add("--force-overwrites");

        if ("mp3".equals(format)) {
            // --- LÓGICA AUDIO ---
            String bitrate = (qualityParam == null) ? "128K" : qualityParam + "K";
            if (!bitrate.matches("\\d+K")) bitrate = "128K"; // Validación extra

            commands.add("-x"); // Extraer audio
            commands.add("--audio-format");
            commands.add("mp3");
            commands.add("--audio-quality");
            commands.add(bitrate);

            // Metadatos para que Windows muestre el bitrate y la carátula
            commands.add("--add-metadata");
            commands.add("--embed-thumbnail");

        } else {
            // --- LÓGICA VIDEO ---
            // qualityParam es la altura (ej: 720, 1080)
            String res = (qualityParam == null) ? "720" : qualityParam;

            commands.add("-f");
            // Baja el mejor video hasta X resolución + el mejor audio y los une
            commands.add("bestvideo[height<=" + res + "]+bestaudio/best[height<=" + res + "]");
            commands.add("--merge-output-format");
            commands.add("mp4");
        }

        commands.add(videoUrl);

        // 5. GUARDAR HISTORIAL
        DownloadHistory history = DownloadHistory.builder()
                .videoUrl(videoUrl)
                .videoTitle(videoTitle)
                .format(format)
                .build();
        historyRepository.save(history);

        // 6. EJECUTAR PROCESO DE DESCARGA
        ProcessBuilder processBuilder = new ProcessBuilder(commands);

        // Descartamos salida para evitar bloqueos del buffer (Deadlocks)
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor(); // Esperamos a que termine

            if (exitCode != 0) {
                throw new IOException("yt-dlp falló con código: " + exitCode);
            }

            // 7. LEER ARCHIVO Y LIMPIAR
            if (!Files.exists(tempFilePath)) {
                throw new IOException("El archivo temporal no se generó.");
            }

            byte[] fileContent = Files.readAllBytes(tempFilePath);
            Files.delete(tempFilePath); // Borramos del disco

            // 8. RETORNAR RESPUESTA
            return new AudioResponse(
                    new org.springframework.core.io.ByteArrayResource(fileContent),
                    videoTitle + "." + format
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("El proceso de descarga fue interrumpido", e);
        }
    }

}
