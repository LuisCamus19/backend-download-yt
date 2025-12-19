package pe.edu.upc.backdownloadmusicmp3.dtos;

import lombok.Data;

@Data
public class DownloadRequestDTO {

    private String url;

    private String quality;

}
