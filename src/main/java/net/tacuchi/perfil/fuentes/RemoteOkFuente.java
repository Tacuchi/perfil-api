package net.tacuchi.perfil.fuentes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.clasificacion.Clasificador;
import net.tacuchi.perfil.clasificacion.Texto;
import net.tacuchi.perfil.clasificacion.Ubicaciones;
import net.tacuchi.perfil.config.PerfilProperties;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Modalidad;
import net.tacuchi.perfil.model.OfertaRecogida;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Remote OK, por su API pública. El primer elemento son metadatos y términos. Todo es remoto;
 * se quedan las ofertas de tecnología por sus etiquetas o su puesto. No se guardan logos.
 */
@Component
@RequiredArgsConstructor
public class RemoteOkFuente implements FuenteOfertas {

    /** Etiquetas de desarrollo. Remote OK etiqueta con poca precisión: «sys admin» o «ai» aparecen en puestos comerciales. */
    static final Set<String> ETIQUETAS_TECNOLOGIA = Set.of("dev", "developer", "engineer", "software", "backend",
            "frontend", "front end", "full stack", "fullstack", "mobile", "android", "ios", "devops", "javascript",
            "typescript", "java", "python", "golang", "react", "angular", "flutter", "kotlin");
    private static final Pattern PUESTO_TECNOLOGIA = Pattern.compile(
            "(?<![a-z])(developer|engineer|desarrollador|desarrolladora|programmer|devops|sre)(?![a-z])");
    private static final String RUTA = "/api";

    private final ClienteFuentes cliente;
    private final PerfilProperties propiedades;

    @Override
    public Fuente fuente() {
        return Fuente.REMOTE_OK;
    }

    @Override
    public Lectura leer() {
        JsonNode respuesta = cliente.json(propiedades.ofertas().remoteokUrl() + RUTA);
        if (!respuesta.isArray()) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "la respuesta no es una lista");
        }
        List<OfertaRecogida> ofertas = new ArrayList<>();
        for (JsonNode oferta : respuesta) {
            if (oferta.hasNonNull("id") && oferta.hasNonNull("position") && esDeTecnologia(oferta)) {
                ofertas.add(convertir(oferta));
            }
        }
        return new Lectura(List.copyOf(ofertas), true);
    }

    static boolean esDeTecnologia(JsonNode oferta) {
        for (JsonNode etiqueta : oferta.path("tags")) {
            if (ETIQUETAS_TECNOLOGIA.contains(Texto.normalizar(etiqueta.asString()))) {
                return true;
            }
        }
        return PUESTO_TECNOLOGIA.matcher(Texto.normalizar(oferta.path("position").asString(""))).find();
    }

    static OfertaRecogida convertir(JsonNode oferta) {
        String html = oferta.path("description").asString("");
        String texto = Texto.deHtml(html);
        List<String> etiquetas = new ArrayList<>();
        oferta.path("tags").forEach(t -> etiquetas.add(t.asString()));
        String ubicacion = oferta.path("location").asString("").strip();
        long epoch = oferta.path("epoch").asLong(0);
        return new OfertaRecogida(
                Fuente.REMOTE_OK,
                oferta.path("id").asString(),
                oferta.path("position").asString(),
                oferta.path("company").asString("").strip(),
                epoch > 0 ? Instant.ofEpochSecond(epoch) : null,
                ubicacion.isBlank() ? null : ubicacion,
                Modalidad.REMOTO,
                Ubicaciones.remotoAdmitePeru(ubicacion),
                null,
                Clasificador.detectarIdioma(texto),
                etiquetas.isEmpty() ? texto : texto + "\n" + String.join(", ", etiquetas),
                oferta.path("url").asString(),
                ExtractorCorreo.extraer(html).orElse(null));
    }
}
