package pe.edu.upc.backdownloadmusicmp3.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upc.backdownloadmusicmp3.entities.DownloadHistory;

import java.util.List;

@Repository
public interface DownloadHistoryRepository extends JpaRepository<DownloadHistory, Long> {

    // Metodo personalizado mágico: Spring crea la consulta SQL solo por el nombre del metodo
    // Útil si quieres mostrarle al usuario "sus últimas descargas" si tuvieras usuarios
    List<DownloadHistory> findByVideoUrl(String videoUrl);

    // Para ver las descargas más recientes primero
    List<DownloadHistory> findAllByOrderByDownloadedAtDesc();

}
