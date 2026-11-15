package net.tacuchi.perfil.controller;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.dto.ArchivoDescarga;
import net.tacuchi.perfil.dto.VersionResponse;
import net.tacuchi.perfil.dto.VigenteRequest;
import net.tacuchi.perfil.service.ValidadorArchivos;
import net.tacuchi.perfil.service.VersionesService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Historial del CV. Todas sus rutas exigen a la persona identificada. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VersionesController {

    private final VersionesService versionesService;

    @GetMapping("/versiones")
    public List<VersionResponse> listar() {
        return versionesService.listar();
    }

    @PostMapping(path = "/versiones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VersionResponse subir(
            @RequestPart(ValidadorArchivos.CAMPO_PDF) MultipartFile pdf,
            @RequestPart(name = ValidadorArchivos.CAMPO_WORD, required = false) MultipartFile word) {
        return versionesService.subir(pdf, word);
    }

    @GetMapping("/versiones/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable String id) {
        return descarga(versionesService.descargarPdf(id));
    }

    @GetMapping("/versiones/{id}/word")
    public ResponseEntity<byte[]> descargarWord(@PathVariable String id) {
        return descarga(versionesService.descargarWord(id));
    }

    @PutMapping("/vigente")
    public VersionResponse dejarVigente(@Valid @RequestBody VigenteRequest peticion) {
        return versionesService.dejarVigente(peticion.version());
    }

    private static ResponseEntity<byte[]> descarga(ArchivoDescarga archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.tipo()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(archivo.contenido());
    }
}
