package pe.edu.upc.backdownloadmusicmp3.servicesimplements;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import pe.edu.upc.backdownloadmusicmp3.dtos.AudioResponse;
import pe.edu.upc.backdownloadmusicmp3.servicesinterfaces.IAudioDownloadService;

import java.io.IOException;
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

        System.out.println("--- NUEVA SOLICITUD ---");
        System.out.println("URL: " + videoUrl);

        // 1. OBTENER TÍTULO (Con la corrección para ignorar playlist)
        String videoTitle = "archivo_descargado";
        try {
            ProcessBuilder titlePb = new ProcessBuilder(
                    ytDlpPath,
                    "--get-title",
                    "--no-warnings",
                    "--no-playlist", // <--- IMPORTANTE: Ignorar lista al obtener título
                    videoUrl
            );
            Process p = titlePb.start();
            if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                String rawTitle = new String(p.getInputStream().readAllBytes()).trim();
                if (!rawTitle.isEmpty()) {
                    videoTitle = rawTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ No se pudo obtener título, usando genérico.");
        }

        System.out.println("Título: " + videoTitle);

        // 2. RUTAS
        String uniqueFileName = "temp_" + UUID.randomUUID().toString() + "." + format;
        String systemTempDir = System.getProperty("java.io.tmpdir");
        Path tempFilePath = Paths.get(systemTempDir, uniqueFileName);

        // 3. COMANDO
        List<String> commands = new ArrayList<>();
        commands.add(ytDlpPath);
        commands.add("--ffmpeg-location");
        commands.add(ffmpegDir);

        // --- LA SOLUCIÓN AL "BUCLE" DE MIXES ---
        commands.add("--no-playlist"); // <--- ESTO EVITA QUE DESCARGUE LOS 700 VIDEOS

        commands.add("-o");
        commands.add(tempFilePath.toString());
        commands.add("--force-overwrites");

        if ("mp3".equals(format)) {
            String bitrate = (qualityParam == null) ? "192K" : qualityParam + "K";
            commands.add("-x");
            commands.add("--audio-format");
            commands.add("mp3");
            commands.add("--audio-quality");
            commands.add(bitrate);
            commands.add("--add-metadata");
        } else {
            String res = (qualityParam == null) ? "1080" : qualityParam;
            commands.add("-f");
            commands.add("bestvideo[height<=" + res + "]+bestaudio/best[height<=" + res + "]");
            commands.add("--merge-output-format");
            commands.add("mp4");
        }

        commands.add(videoUrl);

        // 4. EJECUCIÓN MEJORADA (inheritIO)
        // Usamos inheritIO porque es más estable y evita bloqueos de buffer,
        // además te muestra el progreso bonito en la consola.
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.inheritIO();

        try {
            System.out.println("⏳ Ejecutando descarga...");
            Process process = processBuilder.start();

            // Tiempo máximo 15 mins (por si es un video de 10 horas)
            boolean finished = process.waitFor(15, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Tiempo de espera agotado.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("Error en yt-dlp (Código " + exitCode + ")");
            }

            if (!Files.exists(tempFilePath)) {
                throw new IOException("El archivo no se creó.");
            }

            System.out.println("✅ Descarga lista. Enviando al cliente...");
            byte[] fileContent = Files.readAllBytes(tempFilePath);

            try { Files.delete(tempFilePath); } catch (Exception ignored) {}

            return new AudioResponse(
                    new ByteArrayResource(fileContent),
                    videoTitle + "." + format
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Proceso interrumpido", e);
        }
    }

}
