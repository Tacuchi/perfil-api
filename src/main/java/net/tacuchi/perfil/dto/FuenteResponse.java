package net.tacuchi.perfil.dto;

import java.time.Instant;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;

/** Estado de una fuente: cómo terminó su última lectura y cuándo fue la última correcta. */
public record FuenteResponse(
        Fuente fuente,
        String nombre,
        EstadoLectura estado,
        String detalle,
        Instant ultimaLectura,
        Instant ultimaCorrecta,
        int ofertasLeidas) {
}
