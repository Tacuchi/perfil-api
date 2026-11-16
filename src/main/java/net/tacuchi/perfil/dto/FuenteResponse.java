package net.tacuchi.perfil.dto;

import java.time.Instant;
import net.tacuchi.perfil.model.EstadoConexion;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Requisito;
import net.tacuchi.perfil.model.Via;

/**
 * Estado de una conexión: cómo terminó su última lectura, cuándo fue la última correcta y cuántas lecturas le
 * quedan hoy (día de Lima); desde dónde se lee, lo que necesita, si lo tiene y si se puede leer; cuántas lecturas
 * admite al día y, si se lee desde la máquina, cuántos avisos por corrida. Los campos solo crecen: el panel
 * desplegado lee los primeros ocho.
 */
public record FuenteResponse(
        Fuente fuente,
        String nombre,
        EstadoLectura estado,
        String detalle,
        Instant ultimaLectura,
        Instant ultimaCorrecta,
        int ofertasLeidas,
        int lecturasRestantes,
        Via via,
        Requisito necesita,
        boolean tieneLoQueNecesita,
        boolean pausada,
        String motivoPausa,
        EstadoConexion estadoConexion,
        int lecturasDia,
        Integer avisosPorCorrida) {
}
