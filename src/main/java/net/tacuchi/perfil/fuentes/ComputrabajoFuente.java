package net.tacuchi.perfil.fuentes;

import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.clasificacion.*;
import net.tacuchi.perfil.config.PerfilProperties;
import net.tacuchi.perfil.model.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class ComputrabajoFuente implements FuenteOfertas {
    private final CicloApify ciclo;
    private final PerfilProperties propiedades;
    private final JsonMapper jsonMapper;

    @Override public Fuente fuente() { return Fuente.COMPUTRABAJO; }

    @Override public Lectura leer() {
        var config = propiedades.computrabajo();
        ObjectNode entrada = jsonMapper.createObjectNode();
        entrada.put("country", "pe");
        config.terminos().forEach(entrada.putArray("searchQueries")::add);
        entrada.put("sortBy", "newest");
        entrada.put("maxResults", config.maxAvisos());
        entrada.put("maxResultsPerQuery", (config.maxAvisos() + config.terminos().size() - 1) / config.terminos().size());
        entrada.put("includeJobDetails", true);
        entrada.put("maxConcurrency", 3);
        var resultado = ciclo.ejecutar(fuente(), config.url(), config.actor(), entrada, config.maxAvisos(),
                new BigDecimal("0.001"), new BigDecimal("0.003"));
        var ofertas = convertir(resultado.avisos(), config.maxAvisos());
        if (ofertas.isEmpty()) {
            throw new LecturaFallidaException(resultado.estado(), resultado.detalle() == null
                    ? "el servicio no devolvió avisos válidos" : resultado.detalle(), resultado.costo());
        }
        return new Lectura(ofertas, false, resultado.costo(), resultado.detalle());
    }

    /** En el orden de llegada, que es el de recientes; sin repetidos y con el máximo configurado. */
    static List<OfertaRecogida> convertir(JsonNode avisos, int maximo) {
        List<OfertaRecogida> ofertas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (JsonNode aviso : avisos) {
            String id = aviso.path("id").asString("");
            String url = url(aviso);
            if (ofertas.size() == maximo) {
                break;
            }
            if (!id.isBlank() && !aviso.path("title").asString("").isBlank() && url != null && vistos.add(id)) {
                ofertas.add(convertir(aviso, id, url));
            }
        }
        return List.copyOf(ofertas);
    }

    private static OfertaRecogida convertir(JsonNode aviso, String id, String url) {
        String texto = texto(aviso);
        String ubicacion = vacioANull(aviso.path("location").asString(""));
        Modalidad modalidad = modalidad(aviso.path("work_arrangement").asString(""));
        String correo = vacioANull(aviso.path("contact_email").asString(""));
        return new OfertaRecogida(
                Fuente.COMPUTRABAJO,
                id,
                aviso.path("title").asString().strip(),
                vacioANull(aviso.path("company").asString("")),
                // La fecha del aviso no ordena: se toma cuando se vio por primera vez.
                null,
                ubicacion,
                modalidad,
                admite(modalidad, aviso.path("country_code").asString(""), ubicacion),
                null,
                Clasificador.detectarIdioma(texto),
                texto,
                url,
                correo != null ? correo.toLowerCase(Locale.ROOT)
                        : ExtractorCorreo.extraer(aviso.path("description_html").asString("")).orElse(null));
    }

    private static String texto(JsonNode aviso) {
        String completo = aviso.path("description_full").asString("");
        return completo.isBlank() ? Texto.deHtml(aviso.path("description_html").asString("")) : completo.strip();
    }

    /** El aviso en Computrabajo; si no lo trae, la página para postular. */
    private static String url(JsonNode aviso) {
        for (String campo : List.of("source_url", "apply_url")) {
            String url = aviso.path(campo).asString("");
            if (url.startsWith("https://")) {
                return url;
            }
        }
        return null;
    }

    private static Modalidad modalidad(String valor) {
        return switch (valor) {
            case "Remote" -> Modalidad.REMOTO;
            case "Hybrid" -> Modalidad.HIBRIDO;
            case "On-site" -> Modalidad.PRESENCIAL;
            default -> Modalidad.NO_INFORMA;
        };
    }

    /** Todo aviso es de Perú: remoto admite a la persona; híbrido o presencial, si es en Lima. */
    private static Boolean admite(Modalidad modalidad, String pais, String ubicacion) {
        if (!"PE".equalsIgnoreCase(pais)) {
            return null;
        }
        return Ubicaciones.admiteEnPeru(modalidad, ubicacion);
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

}
