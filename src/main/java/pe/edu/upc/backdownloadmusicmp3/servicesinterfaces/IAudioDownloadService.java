package pe.edu.upc.backdownloadmusicmp3.servicesinterfaces;

import org.springframework.core.io.Resource;
import pe.edu.upc.backdownloadmusicmp3.dtos.AudioResponse;

import java.io.IOException;

public interface IAudioDownloadService {

    AudioResponse downloadAudio(String videoUrl, String quality) throws IOException;

    AudioResponse downloadVideo(String videoUrl, String quality) throws IOException;

}
