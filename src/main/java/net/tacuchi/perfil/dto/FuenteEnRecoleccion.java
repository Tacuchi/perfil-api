package net.tacuchi.perfil.dto;

import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.PasoFuente;

/**
 * Una fuente lanzada en una recolección. Resultado y ofertas leídas solo llegan cuando su paso es TERMINADA.
 */
public record FuenteEnRecoleccion(
        Fuente fuente,
        String nombre,
        PasoFuente paso,
        EstadoLectura resultado,
        Integer ofertasLeidas, java.math.BigDecimal costoUsd, String detalle, boolean parcial) {

    public static FuenteEnRecoleccion pendiente(Fuente fuente) {
        return new FuenteEnRecoleccion(fuente, fuente.nombre(), PasoFuente.PENDIENTE, null, null, null, null, false);
    }

    public FuenteEnRecoleccion leyendo() {
        return new FuenteEnRecoleccion(fuente, nombre, PasoFuente.LEYENDO, null, null, null, null, false);
    }

    public FuenteEnRecoleccion terminada(EstadoLectura resultadoLectura, int ofertas) {
        return terminada(resultadoLectura, ofertas, null, null);
    }

    public FuenteEnRecoleccion terminada(EstadoLectura resultadoLectura, int ofertas,
            java.math.BigDecimal costoUsd, String detalle) {
        return new FuenteEnRecoleccion(fuente, nombre, PasoFuente.TERMINADA, resultadoLectura, ofertas, costoUsd, detalle, resultadoLectura.parcial(detalle));
    }
}
