package net.tacuchi.perfil.clasificacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.model.Clasificacion;
import net.tacuchi.perfil.model.Encaje;
import net.tacuchi.perfil.model.Modalidad;
import net.tacuchi.perfil.model.OfertaRecogida;
import org.springframework.stereotype.Component;

/**
 * Clasifica una oferta con reglas fijas, en español y en inglés. Cada motivo sale de la regla que se cumplió.
 * Un dato que la oferta no informa nunca la descarta: queda anotado en los motivos.
 */
@Component
@RequiredArgsConstructor
public class Clasificador {

    static final String INGLES_CONVERSADO = "Pide inglés conversado.";
    static final String NIVEL_JUNIOR = "Es de nivel junior o prácticas.";
    static final String SIN_TECNOLOGIAS = "No menciona ninguna tecnología del CV.";
    static final String JEFATURA = "Es una jefatura de equipo.";
    static final String NO_INFORMA_MODALIDAD = "No informa la modalidad.";
    static final String NO_INFORMA_PAISES = "Es remota y no informa desde qué países admite candidatos.";
    static final String NO_INFORMA_CIUDAD = "No informa la ciudad.";
    static final String NO_INFORMA_NIVEL = "No informa el nivel.";
    static final String NO_INFORMA_IDIOMA = "No informa el idioma de trabajo.";
    static final String EN_INGLES_SIN_NIVEL = "Está en inglés y no informa el nivel de inglés exigido.";
    static final String REMOTA_DESDE_PERU = "Remota, admite candidatos desde Perú.";
    static final String EN_LIMA = "%s en Lima.";
    static final String REMOTA_SOLO_PARA = "Es remota solo para: %s.";
    static final String FUERA_DE_LIMA = "Es %s fuera de Lima: %s.";
    static final String PRINCIPALES = "Menciona tecnologías principales: %s.";
    static final String SOLO_SECUNDARIAS = "Solo menciona tecnologías secundarias: %s.";

    private static final List<Pattern> PIDE_INGLES = List.of(
            Pattern.compile("ingles[^.]{0,40}(avanzado|fluido|fluidez|conversacional|nativo|bilingue|b2|c1|c2)"),
            Pattern.compile("(avanzado|fluido|fluidez|conversacional|nativo|b2|c1|c2)[^.]{0,25}ingles"),
            Pattern.compile("(?<![a-z])bilingue"),
            Pattern.compile("dominio (del |de )?ingles"),
            Pattern.compile("(fluent|fluency|proficient|proficiency|advanced|native|excellent|strong|professional)"
                    + "[^.]{0,40}english"),
            Pattern.compile("english[^.]{0,30}(fluent|fluency|proficien|(?<![a-z0-9])(b2|c1|c2)(?![a-z0-9]))"),
            Pattern.compile("(written and verbal|verbal and written|written and spoken|spoken and written)"
                    + "[^.]{0,30}english"));
    private static final Pattern DESEABLE = Pattern.compile(
            "(?<![a-z])(deseable|valorable|valorado|plus|nice to have|bonus|preferred|preferible)(?![a-z])");
    private static final Pattern MENCIONA_INGLES = Pattern.compile("(?<![a-z])(ingles|english)(?![a-z])");
    private static final Pattern TITULO_JUNIOR = Pattern.compile("(?<![a-z])(junior|jr|trainee|intern|internship"
            + "|practicas|practicante|pasante|pasantia|becario|becaria|aprendiz|entry level)(?![a-z])");
    private static final Pattern TITULO_CON_NIVEL = Pattern.compile("(?<![a-z])(junior|jr|senior|sr|semi senior|ssr"
            + "|mid|middle|lead|principal|staff|expert|experto|trainee|intern|practicante)(?![a-z])");
    private static final Pattern TITULO_JEFATURA = Pattern.compile("(?<![a-z])(jefe|jefa|jefatura|gerente|director"
            + "|directora|head of|vp|vice president|cto|team lead|team leader|lider de equipo|engineering manager"
            + "|development manager|software manager|it manager|manager de)(?![a-z])");
    private static final Set<String> PALABRAS_ES = Set.of("de", "la", "que", "el", "en", "los", "para", "con", "las",
            "del", "una", "por", "experiencia");
    private static final Set<String> PALABRAS_EN = Set.of("the", "and", "to", "of", "with", "for", "you", "we",
            "our", "your", "experience", "will");

    private final CatalogoTecnologias catalogo;

    public Clasificacion clasificar(OfertaRecogida oferta) {
        String titulo = Texto.normalizar(oferta.titulo());
        String texto = titulo + ". " + Texto.normalizar(oferta.descripcion());
        List<String> descartes = new ArrayList<>();
        List<String> notas = new ArrayList<>();

        idioma(oferta, texto, descartes, notas);
        nivel(oferta, titulo, descartes, notas);
        if (TITULO_JEFATURA.matcher(titulo).find()) {
            descartes.add(JEFATURA);
        }
        ubicacion(oferta, descartes, notas);

        List<String> principales = CatalogoTecnologias.mencionadas(catalogo.principales(), texto);
        List<String> secundarias = CatalogoTecnologias.mencionadas(catalogo.secundarias(), texto);
        List<String> tecnologias = new ArrayList<>(principales);
        tecnologias.addAll(secundarias);

        Encaje encaje;
        List<String> motivos = new ArrayList<>();
        if (tecnologias.isEmpty()) {
            descartes.add(SIN_TECNOLOGIAS);
        }
        if (!descartes.isEmpty()) {
            encaje = Encaje.NO_ENCAJA;
            motivos.addAll(descartes);
        } else if (!principales.isEmpty()) {
            encaje = Encaje.ALTO;
            motivos.add(PRINCIPALES.formatted(String.join(", ", principales)));
        } else {
            encaje = Encaje.MEDIO;
            motivos.add(SOLO_SECUNDARIAS.formatted(String.join(", ", secundarias)));
        }
        motivos.addAll(notas);
        return new Clasificacion(encaje, List.copyOf(motivos), List.copyOf(tecnologias));
    }

    /** Idioma de la publicación, para las fuentes que no lo declaran: el que más palabras frecuentes aporta. */
    public static String detectarIdioma(String texto) {
        int es = 0;
        int en = 0;
        for (String palabra : Texto.normalizar(texto).split("[^a-z]+")) {
            if (PALABRAS_ES.contains(palabra)) {
                es++;
            } else if (PALABRAS_EN.contains(palabra)) {
                en++;
            }
        }
        if (es == en) {
            return null;
        }
        return es > en ? "es" : "en";
    }

    private static void idioma(OfertaRecogida oferta, String texto, List<String> descartes, List<String> notas) {
        if (pideInglesConversado(texto)) {
            descartes.add(INGLES_CONVERSADO);
            return;
        }
        String idioma = oferta.idioma() != null ? oferta.idioma() : detectarIdioma(oferta.descripcion());
        if ("en".equals(idioma)) {
            notas.add(EN_INGLES_SIN_NIVEL);
        } else if (!MENCIONA_INGLES.matcher(texto).find()) {
            notas.add(NO_INFORMA_IDIOMA);
        }
    }

    private static boolean pideInglesConversado(String texto) {
        for (Pattern patron : PIDE_INGLES) {
            Matcher coincidencia = patron.matcher(texto);
            while (coincidencia.find()) {
                if (!DESEABLE.matcher(oracion(texto, coincidencia.start(), coincidencia.end())).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String oracion(String texto, int inicio, int fin) {
        int desde = texto.lastIndexOf('.', inicio) + 1;
        int hasta = texto.indexOf('.', fin);
        return texto.substring(desde, hasta < 0 ? texto.length() : hasta);
    }

    private static void nivel(OfertaRecogida oferta, String titulo, List<String> descartes, List<String> notas) {
        if (Boolean.TRUE.equals(oferta.nivelJunior()) || TITULO_JUNIOR.matcher(titulo).find()) {
            descartes.add(NIVEL_JUNIOR);
        } else if (oferta.nivelJunior() == null && !TITULO_CON_NIVEL.matcher(titulo).find()) {
            notas.add(NO_INFORMA_NIVEL);
        }
    }

    private static void ubicacion(OfertaRecogida oferta, List<String> descartes, List<String> notas) {
        Modalidad modalidad = oferta.modalidad() == null ? Modalidad.NO_INFORMA : oferta.modalidad();
        Boolean admite = oferta.ubicacionAdmite();
        String lugar = oferta.ubicacion() == null || oferta.ubicacion().isBlank() ? "sin detalle" : oferta.ubicacion();
        switch (modalidad) {
            case NO_INFORMA -> notas.add(NO_INFORMA_MODALIDAD);
            case REMOTO -> {
                if (admite == null) {
                    notas.add(NO_INFORMA_PAISES);
                } else if (admite) {
                    notas.add(REMOTA_DESDE_PERU);
                } else {
                    descartes.add(REMOTA_SOLO_PARA.formatted(lugar));
                }
            }
            case HIBRIDO, PRESENCIAL -> {
                String nombre = modalidad == Modalidad.HIBRIDO ? "híbrida" : "presencial";
                if (admite == null) {
                    notas.add(NO_INFORMA_CIUDAD);
                } else if (admite) {
                    notas.add(EN_LIMA.formatted(modalidad == Modalidad.HIBRIDO ? "Híbrida" : "Presencial"));
                } else {
                    descartes.add(FUERA_DE_LIMA.formatted(nombre, lugar));
                }
            }
        }
    }
}
