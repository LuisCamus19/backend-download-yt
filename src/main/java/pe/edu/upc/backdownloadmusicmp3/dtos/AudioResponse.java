package pe.edu.upc.backdownloadmusicmp3.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.Resource;

@Data
@AllArgsConstructor
public class AudioResponse {
    private Resource resource;
    private String filename;
}
