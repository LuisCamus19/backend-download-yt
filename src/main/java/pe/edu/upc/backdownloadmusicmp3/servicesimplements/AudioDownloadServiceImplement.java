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

    // Rutas inyectadas desde application.properties (o variables de entorno en Render)
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

        // 1. VALIDACIONES
        String youtubeRegex = "^(https?://)?(www\\.|m\\.)?(youtube\\.com|youtu\\.be)/.+$";
        if (videoUrl == null || !videoUrl.matches(youtubeRegex)) {
            throw new IllegalArgumentException("La URL proporcionada no es válida.");
        }

        // 2. OBTENER TÍTULO (Con Timeout de seguridad)
        String videoTitle = "Archivo_Descargado";
        try {
            ProcessBuilder titlePb = new ProcessBuilder(
                    ytDlpPath,
                    "--get-title",
                    "--force-ipv4",
                    "--no-warnings",
                    "--no-playlist",
                    "--socket-timeout", "5",
                    videoUrl
            );
            // Descartamos errores para no llenar logs
            titlePb.redirectError(ProcessBuilder.Redirect.DISCARD);

            Process p = titlePb.start();
            if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                videoTitle = new String(p.getInputStream().readAllBytes()).trim();
            } else {
                if (p.isAlive()) p.destroyForcibly();
            }
        } catch (Exception e) {
            System.err.println("Advertencia obteniendo título: " + e.getMessage());
        }

        // Limpieza de nombre
        videoTitle = videoTitle.replaceAll("[\\\\/:*?\"<>|]", "");
        if (videoTitle.isEmpty()) videoTitle = "media_file";

        // 3. CONFIGURAR RUTA TEMPORAL (Compatible con Linux/Render)
        String uniqueFileName = "temp_" + UUID.randomUUID().toString() + "." + format;

        // Usamos la carpeta temporal del sistema (/tmp en Linux) para evitar errores de permisos
        String systemTempDir = System.getProperty("java.io.tmpdir");
        Path tempFilePath = Paths.get(systemTempDir, uniqueFileName);

        // 4. CONSTRUIR COMANDO (OPTIMIZADO PARA POCA RAM)
        List<String> commands = new ArrayList<>();
        commands.add(ytDlpPath);
        commands.add("--ffmpeg-location");
        commands.add(ffmpegDir);
        commands.add("--force-ipv4");
        commands.add("--no-playlist");
        commands.add("-o");
        commands.add(tempFilePath.toString());
        commands.add("--force-overwrites");

        // Buffer pequeño para escribir a disco rápido y no llenar la RAM
        commands.add("--buffer-size");
        commands.add("1024");

        if ("mp3".equals(format)) {
            // --- LÓGICA MP3 (AUDIO) ---
            String bitrate = (qualityParam == null) ? "128K" : qualityParam + "K";
            if (!bitrate.matches("\\d+K")) bitrate = "128K";

            commands.add("-x"); // Extraer audio
            commands.add("--audio-format");
            commands.add("mp3");
            commands.add("--audio-quality");
            commands.add(bitrate);
            commands.add("--add-metadata");
        } else {
            // --- LÓGICA MP4 (VIDEO) - MODO LIGERO ---
            String res = (qualityParam == null) ? "720" : qualityParam;

            commands.add("-f");

            commands.add("best[ext=mp4][height<=" + res + "]/best[ext=mp4]/best");
        }

        commands.add(videoUrl);

        // 5. GUARDAR HISTORIAL
        try {
            DownloadHistory history = DownloadHistory.builder()
                    .videoUrl(videoUrl)
                    .videoTitle(videoTitle)
                    .format(format)
                    .build();
            historyRepository.save(history);
        } catch (Exception e) {
            System.err.println("Error guardando historial: " + e.getMessage());
        }

        // 6. EJECUTAR DESCARGA
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                // Si falla yt-dlp, lanzamos error
                throw new IOException("Error en la descarga. Código de salida: " + exitCode);
            }

            // Verificar si el archivo existe
            if (!Files.exists(tempFilePath)) {
                throw new IOException("El archivo temporal no se generó.");
            }

            // Leer bytes y borrar archivo del disco
            byte[] fileContent = Files.readAllBytes(tempFilePath);
            Files.delete(tempFilePath);

            // 7. RETORNAR
            return new AudioResponse(
                    new org.springframework.core.io.ByteArrayResource(fileContent),
                    videoTitle + "." + format
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Proceso interrumpido", e);
        }
    }

}
