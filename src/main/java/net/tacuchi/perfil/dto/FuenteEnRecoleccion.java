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
        Integer ofertasLeidas) {

    public static FuenteEnRecoleccion pendiente(Fuente fuente) {
        return new FuenteEnRecoleccion(fuente, fuente.nombre(), PasoFuente.PENDIENTE, null, null);
    }

    public FuenteEnRecoleccion leyendo() {
        return new FuenteEnRecoleccion(fuente, nombre, PasoFuente.LEYENDO, null, null);
    }

    public FuenteEnRecoleccion terminada(EstadoLectura resultadoLectura, int ofertas) {
        return new FuenteEnRecoleccion(fuente, nombre, PasoFuente.TERMINADA, resultadoLectura, ofertas);
    }
}
