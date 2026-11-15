package net.tacuchi.perfil.clasificacion;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/** Utilidades de texto para leer ofertas en español y en inglés. */
public final class Texto {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARADORES = Pattern.compile("[-_/]+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");
    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");
    private static final Pattern BLOQUES = Pattern.compile("(?i)<\\s*(br|/p|/li|/div|/h[1-6]|/tr)\\s*/?>");
    private static final Pattern ETIQUETAS = Pattern.compile("<[^>]*>");

    private Texto() {
    }

    /** Minúsculas, sin acentos y con guiones o barras como espacios: «Semi-Senior Inglés» → «semi senior ingles». */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = MARCAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        String separado = SEPARADORES.matcher(sinAcentos.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return ESPACIOS.matcher(separado).replaceAll(" ").trim();
    }

    /** Solo letras y dígitos, para comparar nombres sin importar mayúsculas, acentos ni signos. */
    public static String clave(String texto) {
        return NO_ALFANUMERICO.matcher(normalizar(texto)).replaceAll("");
    }

    /** Texto plano de un fragmento HTML: se quitan las etiquetas, se resuelven las entidades y se conservan los saltos. */
    public static String deHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String conSaltos = BLOQUES.matcher(html).replaceAll("\n");
        String sinEtiquetas = HtmlUtils.htmlUnescape(ETIQUETAS.matcher(conSaltos).replaceAll(" "));
        StringBuilder resultado = new StringBuilder();
        for (String linea : sinEtiquetas.split("\n")) {
            String limpia = ESPACIOS.matcher(linea).replaceAll(" ").trim();
            if (!limpia.isEmpty()) {
                resultado.append(limpia).append('\n');
            }
        }
        return resultado.toString().trim();
    }

    /** Patrón que encuentra la frase ya normalizada como palabra completa, no dentro de otra («java» no está en «javascript»). */
    public static Pattern palabra(String frase) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(normalizar(frase)) + "(?![a-z0-9])");
    }
}
