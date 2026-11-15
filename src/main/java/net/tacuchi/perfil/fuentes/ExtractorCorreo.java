package net.tacuchi.perfil.fuentes;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** El correo de contacto sale solo del texto de la oferta: ninguna fuente lo publica como dato. */
public final class ExtractorCorreo {

    private static final Pattern MAILTO = Pattern.compile("(?i)mailto:([^\"'?>\\s]+@[^\"'?>\\s]+)");
    private static final Pattern CORREO = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
    private static final Pattern NO_CONTACTO = Pattern.compile("(?i)^(no-?reply|do-?not-?reply|noreply)@");

    private ExtractorCorreo() {
    }

    public static Optional<String> extraer(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        Matcher enlace = MAILTO.matcher(html);
        while (enlace.find()) {
            Optional<String> correo = valido(enlace.group(1));
            if (correo.isPresent()) {
                return correo;
            }
        }
        Matcher suelto = CORREO.matcher(html);
        while (suelto.find()) {
            Optional<String> correo = valido(suelto.group());
            if (correo.isPresent()) {
                return correo;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> valido(String candidato) {
        Matcher correo = CORREO.matcher(candidato);
        if (!correo.find() || NO_CONTACTO.matcher(correo.group()).find()) {
            return Optional.empty();
        }
        return Optional.of(correo.group().toLowerCase(Locale.ROOT));
    }
}
