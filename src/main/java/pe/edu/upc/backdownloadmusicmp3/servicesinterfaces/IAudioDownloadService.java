package pe.edu.upc.backdownloadmusicmp3.servicesinterfaces;

import org.springframework.core.io.Resource;
import pe.edu.upc.backdownloadmusicmp3.dtos.AudioResponse;

import java.io.IOException;

public interface IAudioDownloadService {

    AudioResponse downloadAudio(String videoUrl, String quality) throws IOException;

    // NUEVO: Metodo para video
    // 'quality' aquí representará la resolución (ej: "720", "1080")
    AudioResponse downloadVideo(String videoUrl, String quality) throws IOException;

}
