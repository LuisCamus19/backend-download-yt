package pe.edu.upc.backdownloadmusicmp3.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.backdownloadmusicmp3.dtos.AudioResponse;
import pe.edu.upc.backdownloadmusicmp3.dtos.DownloadRequestDTO;
import pe.edu.upc.backdownloadmusicmp3.servicesinterfaces.IAudioDownloadService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/downloads")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", exposedHeaders = {"Content-Disposition", "X-Filename"})
public class DownloadController {

    private final IAudioDownloadService downloadService;

    @PostMapping("/mp3")
    public ResponseEntity<?> downloadMp3(@RequestBody DownloadRequestDTO request) {
        try {
            AudioResponse response = downloadService.downloadAudio(request.getUrl(), request.getQuality());

            String originalFilename = response.getFilename();
            String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8);

            encodedFilename = encodedFilename.replace("+", "%20");

            ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                    .filename("download.mp3")
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                    .header("X-Filename", encodedFilename)
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(response.getResource());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Error de validación: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error en el servidor: " + e.getMessage());
        }
    }

    @PostMapping("/video")
    public ResponseEntity<?> downloadVideo(@RequestBody DownloadRequestDTO request) {
        try {
            AudioResponse response = downloadService.downloadVideo(request.getUrl(), request.getQuality());

            String originalFilename = response.getFilename();
            String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8).replace("+", "%20");

            ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                    .filename("video.mp4")
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                    .header("X-Filename", encodedFilename)
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .body(response.getResource());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error en el servidor: " + e.getMessage());
        }
    }

}
