package net.tacuchi.perfil.dto;

import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;
import net.tacuchi.perfil.model.EstadoRecoleccion;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.OrigenRecoleccion;
import net.tacuchi.perfil.model.PasoFuente;

/**
 * Avance de la recolección en curso o resultado de la última. {@code terminadas} y {@code total} cuentan solo
 * las fuentes lanzadas; las omitidas van aparte.
 */
public record RecoleccionResponse(
        EstadoRecoleccion estado,
        OrigenRecoleccion origen,
        Instant inicio,
        Instant fin,
        int terminadas,
        int total,
        List<FuenteEnRecoleccion> fuentes,
        List<FuenteOmitida> omitidas) {

    public static final RecoleccionResponse NINGUNA =
            new RecoleccionResponse(EstadoRecoleccion.NINGUNA, null, null, null, 0, 0, List.of(), List.of());

    public static RecoleccionResponse iniciada(OrigenRecoleccion origen, Instant inicio, List<Fuente> lanzadas,
            List<FuenteOmitida> omitidas) {
        return new RecoleccionResponse(EstadoRecoleccion.EN_CURSO, origen, inicio, null, 0, lanzadas.size(),
                lanzadas.stream().map(FuenteEnRecoleccion::pendiente).toList(),
                List.copyOf(omitidas));
    }

    public RecoleccionResponse conFuente(Fuente fuente, UnaryOperator<FuenteEnRecoleccion> cambio) {
        List<FuenteEnRecoleccion> nuevas = fuentes.stream()
                .map(f -> f.fuente() == fuente ? cambio.apply(f) : f)
                .toList();
        int hechas = (int) nuevas.stream().filter(f -> f.paso() == PasoFuente.TERMINADA).count();
        return new RecoleccionResponse(estado, origen, inicio, fin, hechas, total, nuevas, omitidas);
    }

    public RecoleccionResponse terminada(Instant cuando) {
        return new RecoleccionResponse(EstadoRecoleccion.TERMINADA, origen, inicio, cuando, terminadas, total,
                fuentes, omitidas);
    }
}
