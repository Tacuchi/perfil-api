package net.tacuchi.perfil.model;

import java.time.Instant;

/** Resultado guardado de la última lectura de una fuente. */
public record LecturaFuente(
        Fuente fuente,
        EstadoLectura estado,
        String detalle,
        Instant ultimaLectura,
        Instant ultimaCorrecta,
        int ofertasLeidas) {
}
