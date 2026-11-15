package net.tacuchi.perfil.model;

import java.time.Instant;
import java.util.Set;

/** Filtro ya resuelto: conjuntos no vacíos y fechas como instantes. Un campo null no filtra. */
public record FiltroOfertas(
        Set<Encaje> encajes,
        Set<EstadoOferta> estados,
        Set<Fuente> fuentes,
        Set<Modalidad> modalidades,
        String tecnologia,
        Instant desde,
        Instant hastaExclusive) {
}
