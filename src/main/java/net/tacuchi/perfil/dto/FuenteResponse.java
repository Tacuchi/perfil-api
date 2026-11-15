package net.tacuchi.perfil.dto;

import java.time.Instant;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;

/**
 * Estado de una fuente: cómo terminó su última lectura, cuándo fue la última correcta y cuántas lecturas le quedan
 * hoy (día de Lima).
 */
public record FuenteResponse(
        Fuente fuente,
        String nombre,
        EstadoLectura estado,
        String detalle,
        Instant ultimaLectura,
        Instant ultimaCorrecta,
        int ofertasLeidas,
        int lecturasRestantes) {
}
