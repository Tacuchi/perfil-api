package net.tacuchi.perfil.clasificacion;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Tecnologías del CV, principales y secundarias, leídas de ofertas/tecnologias.json. */
@Component
public class CatalogoTecnologias {

    static final String ARCHIVO = "ofertas/tecnologias.json";

    private final List<Tecnologia> principales;
    private final List<Tecnologia> secundarias;

    public CatalogoTecnologias(JsonMapper jsonMapper) {
        JsonNode raiz = leer(jsonMapper);
        this.principales = tecnologias(raiz, "principales");
        this.secundarias = tecnologias(raiz, "secundarias");
    }

    public List<Tecnologia> principales() {
        return principales;
    }

    public List<Tecnologia> secundarias() {
        return secundarias;
    }

    public List<String> nombres() {
        List<String> nombres = new ArrayList<>();
        principales.forEach(t -> nombres.add(t.nombre()));
        secundarias.forEach(t -> nombres.add(t.nombre()));
        return List.copyOf(nombres);
    }

    /** Nombres de las tecnologías del grupo que el texto normalizado menciona, en el orden del catálogo. */
    static List<String> mencionadas(List<Tecnologia> grupo, String textoNormalizado) {
        Set<String> encontradas = new LinkedHashSet<>();
        for (Tecnologia tecnologia : grupo) {
            if (tecnologia.patrones().stream().anyMatch(p -> p.matcher(textoNormalizado).find())) {
                encontradas.add(tecnologia.nombre());
            }
        }
        return List.copyOf(encontradas);
    }

    private static JsonNode leer(JsonMapper jsonMapper) {
        try (InputStream entrada = new ClassPathResource(ARCHIVO).getInputStream()) {
            return jsonMapper.readTree(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("no se pudo leer " + ARCHIVO, e);
        }
    }

    private static List<Tecnologia> tecnologias(JsonNode raiz, String grupo) {
        JsonNode lista = raiz.path(grupo);
        if (!lista.isArray() || lista.isEmpty()) {
            throw new IllegalStateException(ARCHIVO + ": se esperaba una lista no vacía en «" + grupo + "»");
        }
        List<Tecnologia> tecnologias = new ArrayList<>();
        for (JsonNode nodo : lista) {
            List<Pattern> patrones = new ArrayList<>();
            nodo.path("patrones").forEach(p -> patrones.add(Texto.palabra(p.asString())));
            String nombre = nodo.path("nombre").asString();
            if (nombre.isBlank() || patrones.isEmpty()) {
                throw new IllegalStateException(ARCHIVO + ": cada tecnología de «" + grupo
                        + "» necesita «nombre» y al menos un patrón; llegó " + nodo);
            }
            tecnologias.add(new Tecnologia(nombre, List.copyOf(patrones)));
        }
        return List.copyOf(tecnologias);
    }

    public record Tecnologia(String nombre, List<Pattern> patrones) {
    }
}
