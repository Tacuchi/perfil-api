package net.tacuchi.perfil.dto;

import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoOmision;

/** Una fuente que la recolección no leyó, con el motivo. */
public record FuenteOmitida(Fuente fuente, String nombre, MotivoOmision motivo, java.math.BigDecimal gastado, java.math.BigDecimal topeMensual) {

    public static FuenteOmitida de(Fuente fuente, MotivoOmision motivo) {
        return new FuenteOmitida(fuente, fuente.nombre(), motivo, null, null);
    }
}
