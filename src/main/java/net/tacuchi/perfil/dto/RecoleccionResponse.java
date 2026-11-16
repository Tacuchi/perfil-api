package net.tacuchi.perfil.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.tacuchi.perfil.model.EstadoRecoleccion;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoCierre;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.model.OrigenRecoleccion;
import net.tacuchi.perfil.model.PasoFuente;

/**
 * Avance de la recolección en curso o resultado de la última. {@code terminadas} y {@code total} cuentan solo
 * las fuentes lanzadas; las omitidas van aparte. {@code cierre} dice por qué una corrida local se cerró sin que el
 * lector la terminara; en cualquier otro caso es null. Los campos solo crecen.
 */
public record RecoleccionResponse(
        EstadoRecoleccion estado,
        OrigenRecoleccion origen,
        Instant inicio,
        Instant fin,
        int terminadas,
        int total,
        List<FuenteEnRecoleccion> fuentes,
        List<FuenteOmitida> omitidas,
        MotivoCierre cierre) {

    public static final RecoleccionResponse NINGUNA =
            new RecoleccionResponse(EstadoRecoleccion.NINGUNA, null, null, null, 0, 0, List.of(), List.of(), null);

    public static RecoleccionResponse iniciada(OrigenRecoleccion origen, Instant inicio, List<Fuente> lanzadas,
            List<FuenteOmitida> omitidas) {
        return new RecoleccionResponse(EstadoRecoleccion.EN_CURSO, origen, inicio, null, 0, lanzadas.size(),
                lanzadas.stream().map(FuenteEnRecoleccion::pendiente).toList(),
                List.copyOf(omitidas), null);
    }

    public RecoleccionResponse conFuente(Fuente fuente, UnaryOperator<FuenteEnRecoleccion> cambio) {
        List<FuenteEnRecoleccion> nuevas = fuentes.stream()
                .map(f -> f.fuente() == fuente ? cambio.apply(f) : f)
                .toList();
        return new RecoleccionResponse(estado, origen, inicio, fin, terminadasDe(nuevas), total, nuevas, omitidas,
                cierre);
    }

    /** Una fuente lanzada que al final no se lee: pasa a las omitidas con su motivo. */
    public RecoleccionResponse sinFuente(Fuente fuente, MotivoOmision motivo) {
        List<FuenteEnRecoleccion> nuevas = fuentes.stream().filter(f -> f.fuente() != fuente).toList();
        List<FuenteOmitida> conOmitida = new ArrayList<>(omitidas);
        conOmitida.add(FuenteOmitida.de(fuente, motivo));
        return new RecoleccionResponse(estado, origen, inicio, fin, terminadasDe(nuevas), nuevas.size(), nuevas,
                List.copyOf(conOmitida), cierre);
    }

    public RecoleccionResponse terminada(Instant cuando) {
        return terminada(cuando, null);
    }

    public RecoleccionResponse terminada(Instant cuando, MotivoCierre motivo) {
        return new RecoleccionResponse(EstadoRecoleccion.TERMINADA, origen, inicio, cuando, terminadas, total,
                fuentes, omitidas, motivo);
    }

    private static int terminadasDe(List<FuenteEnRecoleccion> fuentes) {
        return (int) fuentes.stream().filter(f -> f.paso() == PasoFuente.TERMINADA).count();
    }
}
