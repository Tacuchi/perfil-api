package net.tacuchi.perfil.fuentes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
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
 * Get on Board, por su API pública sin registro: las categorías de tecnología, página a página.
 * Las referencias (ciudad, etiquetas) no se resuelven: costarían una llamada más por cada una.
 */
@Component
@RequiredArgsConstructor
public class GetOnBoardFuente implements FuenteOfertas {

    static final List<String> CATEGORIAS = List.of("programming", "mobile-developer", "sysadmin-devops-qa",
            "machine-learning-ai");
    static final int POR_PAGINA = 50;
    static final int TOPE_PAGINAS = 6;
    /** Catálogo de seniority de la API (/api/v0/seniorities): 1 sin experiencia, 2 junior. */
    private static final Set<String> NIVELES_JUNIOR = Set.of("1", "2");
    private static final String RUTA = "/api/v0/categories/%s/jobs?per_page=%d&page=%d&expand=%%5B%%22company%%22%%5D";

    private final ClienteFuentes cliente;
    private final PerfilProperties propiedades;

    @Override
    public Fuente fuente() {
        return Fuente.GET_ON_BOARD;
    }

    @Override
    public Lectura leer() {
        Map<String, OfertaRecogida> ofertas = new LinkedHashMap<>();
        boolean completa = true;
        for (String categoria : CATEGORIAS) {
            int totalPaginas = 1;
            int pagina = 1;
            for (; pagina <= Math.min(totalPaginas, TOPE_PAGINAS); pagina++) {
                JsonNode respuesta = cliente.json(propiedades.ofertas().getonbrdUrl()
                        + RUTA.formatted(categoria, POR_PAGINA, pagina));
                if (!respuesta.path("data").isArray()) {
                    throw new LecturaFallidaException(EstadoLectura.FALLO,
                            "la respuesta no trae la lista «data»");
                }
                respuesta.path("data").forEach(oferta -> ofertas.putIfAbsent(oferta.path("id").asString(),
                        convertir(oferta)));
                totalPaginas = respuesta.path("meta").path("total_pages").asInt(1);
            }
            completa &= totalPaginas <= TOPE_PAGINAS;
        }
        return new Lectura(List.copyOf(ofertas.values()), completa);
    }

    static OfertaRecogida convertir(JsonNode oferta) {
        JsonNode a = oferta.path("attributes");
        String html = String.join("\n", a.path("description").asString(""), a.path("functions").asString(""),
                a.path("desirable").asString(""), a.path("benefits").asString(""));
        String lang = a.path("lang").asString("");
        List<String> paises = new ArrayList<>();
        a.path("countries").forEach(p -> paises.add(p.asString()));
        String zona = a.path("remote_zone").isNull() ? "" : a.path("remote_zone").asString("");
        Modalidad modalidad = modalidad(a.path("remote_modality").asString(""));
        String ubicacion = ubicacion(paises, zona);
        String seniority = a.path("seniority").path("data").path("id").asString("");
        long publicada = a.path("published_at").asLong(0);
        return new OfertaRecogida(
                Fuente.GET_ON_BOARD,
                oferta.path("id").asString(),
                a.path("title").asString(),
                a.path("company").path("data").path("attributes").path("name").asString(null),
                publicada > 0 ? Instant.ofEpochSecond(publicada) : null,
                ubicacion,
                modalidad,
                admite(modalidad, a.path("remote_modality").asString(""), paises, zona, Texto.deHtml(html)),
                seniority.isEmpty() ? null : NIVELES_JUNIOR.contains(seniority),
                "es".equals(lang) || "en".equals(lang) ? lang : null,
                Texto.deHtml(html),
                oferta.path("links").path("public_url").asString(),
                ExtractorCorreo.extraer(html).orElse(null));
    }

    private static Modalidad modalidad(String valor) {
        return switch (valor) {
            case "fully_remote", "remote_local" -> Modalidad.REMOTO;
            case "hybrid" -> Modalidad.HIBRIDO;
            case "no_remote" -> Modalidad.PRESENCIAL;
            default -> Modalidad.NO_INFORMA;
        };
    }

    private static String ubicacion(List<String> paises, String zona) {
        List<String> partes = new ArrayList<>(paises.stream().filter(p -> !"Remote".equalsIgnoreCase(p)).toList());
        if (!zona.isBlank()) {
            partes.add(zona);
        }
        return partes.isEmpty() ? null : String.join(", ", partes);
    }

    /**
     * Remoto total admite a cualquiera. Remoto «local» admite solo a quien vive en los países o la zona que
     * indica; sin indicarlos, no se sabe. Híbrido o presencial: en Lima si la oferta es de Perú y nombra Lima.
     */
    private static Boolean admite(Modalidad modalidad, String valor, List<String> paises, String zona, String texto) {
        List<String> lugares = paises.stream().filter(p -> !"Remote".equalsIgnoreCase(p)).toList();
        String lugar = String.join(", ", lugares) + (zona.isBlank() ? "" : ", " + zona);
        return switch (modalidad) {
            case REMOTO -> "fully_remote".equals(valor) ? Boolean.TRUE : Ubicaciones.remotoAdmitePeru(lugar);
            case HIBRIDO, PRESENCIAL -> {
                if (lugares.isEmpty()) {
                    yield null;
                }
                boolean enPeru = lugares.stream().anyMatch(p -> Texto.normalizar(p).equals("peru"));
                yield enPeru ? (Ubicaciones.enLima(texto) == Boolean.TRUE ? Boolean.TRUE : null) : Boolean.FALSE;
            }
            case NO_INFORMA -> null;
        };
    }
}
