package net.tacuchi.perfil.service;

import java.util.List;
import java.util.Optional;
import net.tacuchi.perfil.dto.ArchivoDescarga;
import net.tacuchi.perfil.dto.VersionResponse;
import org.springframework.web.multipart.MultipartFile;

public interface VersionesService {

    List<VersionResponse> listar();

    VersionResponse subir(MultipartFile pdf, MultipartFile word);

    ArchivoDescarga descargarPdf(String id);

    ArchivoDescarga descargarWord(String id);

    VersionResponse dejarVigente(String id);

    /** El PDF vigente, o vacío si todavía no hay uno. */
    Optional<byte[]> pdfVigente();
}
