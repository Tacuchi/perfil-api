package net.tacuchi.perfil.service.impl;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.almacen.AlmacenArchivos;
import net.tacuchi.perfil.dto.ArchivoDescarga;
import net.tacuchi.perfil.dto.VersionResponse;
import net.tacuchi.perfil.exception.VersionNoEncontradaException;
import net.tacuchi.perfil.model.Indice;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.model.Version;
import net.tacuchi.perfil.repository.IndiceRepository;
import net.tacuchi.perfil.service.ValidadorArchivos;
import net.tacuchi.perfil.service.VersionesService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class VersionesServiceImpl implements VersionesService {

    private static final String TIPO_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final DateTimeFormatter FECHA_ARCHIVO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").withZone(ZoneOffset.UTC);

    private final AlmacenArchivos almacen;
    private final IndiceRepository indiceRepository;
    private final ValidadorArchivos validador;
    private final Clock clock;

    /** Una sola persona escribe, pero dos pestañas podrían cruzarse: el índice se reescribe entero. */
    private final ReentrantLock escritura = new ReentrantLock();

    @Override
    public List<VersionResponse> listar() {
        Indice indice = indiceRepository.leer();
        return indice.versiones().stream()
                .sorted(Comparator.comparing(Version::fecha).reversed())
                .map(v -> respuesta(v, indice))
                .toList();
    }

    @Override
    public VersionResponse subir(MultipartFile pdf, MultipartFile word) {
        // Todo se valida antes de guardar nada: una carga rechazada no deja rastro.
        byte[] contenidoPdf = validador.validarPdf(pdf);
        byte[] contenidoWord = validador.validarWord(word);
        Version nueva = new Version(UUID.randomUUID().toString(), clock.instant(), contenidoWord != null);

        escritura.lock();
        try {
            guardarArchivos(nueva, contenidoPdf, contenidoWord);
            Indice anterior = indiceRepository.leer();
            Indice indice = anterior.agregar(nueva);
            // Ni la nueva ni la que era vigente salen: la persona pudo haber restaurado la más antigua.
            Set<String> protegidas = anterior.vigente() == null
                    ? Set.of(nueva.id())
                    : Set.of(nueva.id(), anterior.vigente());
            List<Version> excedentes = indice.excedentes(Reglas.TOPE_VERSIONES, protegidas);
            Indice resultado = indice.sin(excedentes);
            indiceRepository.guardar(resultado);
            // El índice manda: se borra después de guardarlo, y un fallo aquí solo deja archivos huérfanos.
            excedentes.forEach(this::borrarArchivos);
            log.info("[CV] versión {} subida; {} expulsadas", nueva.id(), excedentes.size());
            return respuesta(nueva, resultado);
        } finally {
            escritura.unlock();
        }
    }

    @Override
    public ArchivoDescarga descargarPdf(String id) {
        Version version = buscar(indiceRepository.leer(), id);
        return new ArchivoDescarga(leerObligatorio(version.clavePdf()), MediaType.APPLICATION_PDF_VALUE,
                "cv-" + FECHA_ARCHIVO.format(version.fecha()) + ".pdf");
    }

    @Override
    public ArchivoDescarga descargarWord(String id) {
        Version version = buscar(indiceRepository.leer(), id);
        if (!version.conWord()) {
            throw new VersionNoEncontradaException("la versión " + id + " no tiene Word");
        }
        return new ArchivoDescarga(leerObligatorio(version.claveWord()), TIPO_DOCX,
                "cv-" + FECHA_ARCHIVO.format(version.fecha()) + ".docx");
    }

    @Override
    public VersionResponse dejarVigente(String id) {
        escritura.lock();
        try {
            Indice indice = indiceRepository.leer();
            Version version = buscar(indice, id);
            Indice resultado = indice.conVigente(version.id());
            indiceRepository.guardar(resultado);
            log.info("[CV] versión {} vigente", version.id());
            return respuesta(version, resultado);
        } finally {
            escritura.unlock();
        }
    }

    @Override
    public Optional<byte[]> pdfVigente() {
        return indiceRepository.leer().versionVigente().map(v -> leerObligatorio(v.clavePdf()));
    }

    private void guardarArchivos(Version version, byte[] pdf, byte[] word) {
        try {
            almacen.guardar(version.clavePdf(), pdf, MediaType.APPLICATION_PDF_VALUE);
            if (word != null) {
                almacen.guardar(version.claveWord(), word, TIPO_DOCX);
            }
        } catch (RuntimeException e) {
            borrarArchivos(version);
            throw e;
        }
    }

    private void borrarArchivos(Version version) {
        try {
            almacen.borrar(version.clavePdf());
            almacen.borrar(version.claveWord());
        } catch (RuntimeException e) {
            log.warn("[CV] no se pudieron borrar los archivos de la versión {}", version.id(), e);
        }
    }

    /** Un archivo que el índice nombra y el almacén no tiene es una inconsistencia, no un vacío. */
    private byte[] leerObligatorio(String clave) {
        return almacen.leer(clave)
                .orElseThrow(() -> new IllegalStateException("el índice nombra " + clave + " y el almacén no lo tiene"));
    }

    private static Version buscar(Indice indice, String id) {
        return indice.buscar(id).orElseThrow(() -> new VersionNoEncontradaException(id));
    }

    private static VersionResponse respuesta(Version version, Indice indice) {
        return new VersionResponse(version.id(), version.fecha(), version.conWord(),
                version.id().equals(indice.vigente()));
    }
}
