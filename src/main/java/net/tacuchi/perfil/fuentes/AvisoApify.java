package net.tacuchi.perfil.fuentes;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;
import net.tacuchi.perfil.model.*;
import net.tacuchi.perfil.clasificacion.Texto;
import tools.jackson.databind.JsonNode;

/** Conversión de datos públicos: una identidad inválida nunca se reemplaza por un hash nuevo. */
final class AvisoApify {
    private static final Pattern LINKEDIN = Pattern.compile("/jobs/view/(?:[^/]*-)?([0-9]+)/?");
    private static final Pattern BUMERAN = Pattern.compile("/empleos/[^/]*-([0-9]+)\\.html");
    private AvisoApify() { }
    record Enlace(String id, String url) { }

    static Enlace enlace(JsonNode aviso, Fuente fuente) {
        String[] campos = fuente == Fuente.LINKEDIN ? new String[]{"url"} : new String[]{"canonicalUrl", "portalUrl"};
        for (String campo : campos) {
            try {
                URI uri = new URI(aviso.path(campo).asString(""));
                String host = uri.getHost();
                String dominio = fuente == Fuente.LINKEDIN ? "linkedin.com" : "bumeran.com.pe";
                if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || host == null
                        || !(host.equals(dominio) || host.endsWith("." + dominio))
                        || (uri.getPort() != -1 && uri.getPort() != 443)) { continue; }
                var matcher = (fuente == Fuente.LINKEDIN ? LINKEDIN : BUMERAN).matcher(uri.getPath());
                if (!matcher.matches()) { continue; }
                String id = matcher.group(1);
                if (fuente == Fuente.LINKEDIN) {
                    String declarado = aviso.path("id").asString("");
                    if (!declarado.isBlank() && (!declarado.matches("[0-9]+") || !declarado.equals(id))) { continue; }
                }
                String url = uri.toString().split("[?#]", 2)[0];
                return new Enlace(id, url);
            } catch (URISyntaxException e) {
                // Un campo alternativo puede contener el enlace público válido.
            }
        }
        throw new IllegalArgumentException("aviso rechazado: falta un enlace HTTPS compatible con su fuente");
    }

    static String texto(JsonNode aviso, String campo) {
        String valor = aviso.path(campo).asString("").strip();
        return valor.isBlank() ? null : valor;
    }

    static String descripcion(JsonNode aviso) {
        String valor = texto(aviso, "description");
        return valor != null ? valor : Texto.deHtml(aviso.path("descriptionHtml").asString(""));
    }

    static String titulo(JsonNode aviso) {
        String titulo = texto(aviso, "title");
        if (titulo == null) { throw new IllegalArgumentException("aviso rechazado: falta el título"); }
        return titulo;
    }

    static Instant fecha(JsonNode aviso) {
        String valor = texto(aviso, "postedDate");
        if (valor == null) { return null; }
        try { return OffsetDateTime.parse(valor).toInstant(); }
        catch (DateTimeParseException e) {
            try { return LocalDateTime.parse(valor).atZone(Reglas.ZONA_PERSONA).toInstant(); }
            catch (DateTimeParseException sinHora) { return null; }
        }
    }

    static Modalidad modalidad(String valor) {
        if (valor == null) { return Modalidad.NO_INFORMA; }
        return switch (Texto.normalizar(valor)) {
            case "remoto", "remote" -> Modalidad.REMOTO;
            case "hibrido", "hybrid" -> Modalidad.HIBRIDO;
            case "presencial", "on-site" -> Modalidad.PRESENCIAL;
            default -> Modalidad.NO_INFORMA;
        };
    }

    static Boolean junior(String valor) {
        if (valor == null) { return null; }
        String nivel = valor.toLowerCase(Locale.ROOT);
        if (nivel.contains("junior") || nivel.contains("entry") || nivel.contains("sin experiencia")) { return true; }
        if (nivel.contains("senior") || nivel.contains("manager") || nivel.contains("director")
                || nivel.contains("semi")) { return false; }
        return null;
    }
}
