package net.tacuchi.perfil.soporte;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import net.tacuchi.perfil.dto.OfertaEntregada;
import net.tacuchi.perfil.model.Modalidad;

/**
 * Avisos con la forma que entrega el lector de la máquina, ya convertidos. Son de la API, no de un sitio: las
 * respuestas guardadas de cada sitio viven con su lector.
 */
public final class EntregasDePrueba {

    private static final List<String> TITULOS = List.of(
            "Desarrollador Java Junior", "Frontend Developer Angular", "Analista Programador .NET");

    private EntregasDePrueba() {
    }

    /** {@code cuantos} avisos distintos; el prefijo distingue los de una entrega de los de otra. */
    public static List<OfertaEntregada> avisos(String prefijo, int cuantos, Instant publicadaEn) {
        return avisos(prefijo, cuantos, publicadaEn, "https://www.linkedin.com/jobs/view/");
    }

    /** Lo mismo, con los enlaces bajo {@code baseUrl}: el aviso original de otro sitio. */
    public static List<OfertaEntregada> avisos(String prefijo, int cuantos, Instant publicadaEn, String baseUrl) {
        return IntStream.rangeClosed(1, cuantos)
                .mapToObj(n -> new OfertaEntregada(
                        prefijo + "-" + n,
                        TITULOS.get((n - 1) % TITULOS.size()) + " " + prefijo + " " + n,
                        "Empresa " + prefijo + " " + n,
                        publicadaEn,
                        "Lima, Perú",
                        Modalidad.HIBRIDO,
                        Boolean.TRUE,
                        Boolean.TRUE,
                        "es",
                        "Buscamos a alguien con Java, Spring Boot, Angular y SQL para el equipo de producto.",
                        baseUrl + prefijo + "-" + n))
                .toList();
    }
}
