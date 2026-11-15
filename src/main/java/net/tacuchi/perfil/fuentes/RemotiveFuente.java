package net.tacuchi.perfil.fuentes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 * Remotive, por su API pública. Pide no pasar de 4 lecturas al día: se hace una sola, sin parámetros
 * (sus filtros de categoría y límite no funcionan), y la categoría se filtra aquí. Todo es remoto.
 */
@Component
@RequiredArgsConstructor
public class RemotiveFuente implements FuenteOfertas {

    static final Set<String> CATEGORIAS = Set.of("Software Development", "DevOps / Sysadmin", "QA",
            "Artificial Intelligence", "Information Technology", "Data and Analytics");
    private static final String RUTA = "/api/remote-jobs";

    private final ClienteFuentes cliente;
    private final PerfilProperties propiedades;

    @Override
    public Fuente fuente() {
        return Fuente.REMOTIVE;
    }

    @Override
    public Lectura leer() {
        JsonNode respuesta = cliente.json(propiedades.ofertas().remotiveUrl() + RUTA);
        if (!respuesta.path("jobs").isArray()) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "la respuesta no trae la lista «jobs»");
        }
        List<OfertaRecogida> ofertas = new ArrayList<>();
        for (JsonNode oferta : respuesta.path("jobs")) {
            if (CATEGORIAS.contains(oferta.path("category").asString(""))) {
                ofertas.add(convertir(oferta));
            }
        }
        return new Lectura(List.copyOf(ofertas), true);
    }

    static OfertaRecogida convertir(JsonNode oferta) {
        String html = oferta.path("description").asString("");
        String texto = Texto.deHtml(html);
        List<String> etiquetas = new ArrayList<>();
        oferta.path("tags").forEach(t -> etiquetas.add(t.asString()));
        String ubicacion = oferta.path("candidate_required_location").asString("");
        return new OfertaRecogida(
                Fuente.REMOTIVE,
                oferta.path("id").asString(),
                oferta.path("title").asString(),
                oferta.path("company_name").asString("").strip(),
                fecha(oferta.path("publication_date").asString("")),
                ubicacion.isBlank() ? null : ubicacion,
                Modalidad.REMOTO,
                Ubicaciones.remotoAdmitePeru(ubicacion),
                null,
                Clasificador.detectarIdioma(texto),
                etiquetas.isEmpty() ? texto : texto + "\n" + String.join(", ", etiquetas),
                oferta.path("url").asString(),
                ExtractorCorreo.extraer(html).orElse(null));
    }

    /** Remotive da la fecha sin zona; se toma como UTC. */
    private static Instant fecha(String valor) {
        try {
            return valor.isBlank() ? null : LocalDateTime.parse(valor).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
