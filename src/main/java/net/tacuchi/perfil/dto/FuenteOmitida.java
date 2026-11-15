package net.tacuchi.perfil.dto;

import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoOmision;

/** Una fuente que la recolección general no leyó, con el motivo. */
public record FuenteOmitida(Fuente fuente, String nombre, MotivoOmision motivo) {

    public static FuenteOmitida porTope(Fuente fuente) {
        return new FuenteOmitida(fuente, fuente.nombre(), MotivoOmision.TOPE_DIARIO);
    }
}
