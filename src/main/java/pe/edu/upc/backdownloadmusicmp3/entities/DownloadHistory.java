package pe.edu.upc.backdownloadmusicmp3.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "download_history")
@Data // Genera getters, setters, toString, etc.
@NoArgsConstructor // Constructor vacío requerido por JPA
@AllArgsConstructor // Constructor con todos los argumentos
@Builder // Patrón Builder para crear objetos fácilmente
public class DownloadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String videoUrl;

    @Column(length = 255)
    private String videoTitle;

    @Column(length = 20)
    private String format;

    private LocalDateTime downloadedAt;

    // Antes de guardar, asignamos la fecha y hora actual automáticamente
    @PrePersist
    protected void onCreate() {
        downloadedAt = LocalDateTime.now();
    }

}
