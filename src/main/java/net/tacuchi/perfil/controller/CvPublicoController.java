package net.tacuchi.perfil.controller;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.config.PerfilProperties;
import net.tacuchi.perfil.service.PaginaErrorSitio;
import net.tacuchi.perfil.service.VersionesService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La única ruta pública. El PDF se abre en el navegador, sin pedir exclusión a los buscadores,
 * y ninguna caché lo guarda: la siguiente apertura tras una carga ya entrega el nuevo.
 */
@RestController
@RequiredArgsConstructor
public class CvPublicoController {

    public static final String RUTA = "/cv";

    private final VersionesService versionesService;
    private final PaginaErrorSitio paginaErrorSitio;
    private final PerfilProperties propiedades;

    @GetMapping(RUTA)
    public ResponseEntity<byte[]> cvVigente() {
        return versionesService.pdfVigente()
                .map(pdf -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .cacheControl(CacheControl.noStore())
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                                .filename(propiedades.cv().archivo(), StandardCharsets.UTF_8).build().toString())
                        .body(pdf))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                        .cacheControl(CacheControl.noStore())
                        .body(paginaErrorSitio.html()));
    }
}
