package net.tacuchi.perfil.exception;

import java.util.List;
import net.tacuchi.perfil.dto.FuenteOmitida;
import net.tacuchi.perfil.model.MotivoOmision;

/** Un lanzamiento a demanda que no se acepta. El mensaje es para la persona y no lleva detalle interno. */
public class RecoleccionRechazadaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Motivo {
        EN_CURSO, TOPE_DIARIO, PAUSADA, SIN_FUENTES
    }

    private final Motivo motivo;

    private RecoleccionRechazadaException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public static RecoleccionRechazadaException enCurso() {
        return new RecoleccionRechazadaException(Motivo.EN_CURSO,
                "Ya hay una recolección en curso. Espera a que termine para lanzar otra.");
    }

    /** Una sola fuente pedida que no se puede leer ahora, con su motivo. */
    public static RecoleccionRechazadaException omitida(FuenteOmitida omitida, int tope) {
        return switch (omitida.motivo()) {
            case TOPE_DIARIO -> new RecoleccionRechazadaException(Motivo.TOPE_DIARIO,
                    omitida.nombre() + " ya recibió sus " + tope + " lecturas de hoy. Vuelve a intentarlo mañana.");
            case PAUSADA -> new RecoleccionRechazadaException(Motivo.PAUSADA,
                    omitida.nombre() + " está pausada. Reanúdala en Conexiones para leerla.");
        };
    }

    /** Ninguna fuente del lanzamiento general se puede leer ahora. */
    public static RecoleccionRechazadaException sinFuentes(List<FuenteOmitida> omitidas, int tope) {
        boolean todasPorTope = omitidas.stream().allMatch(o -> o.motivo() == MotivoOmision.TOPE_DIARIO);
        return new RecoleccionRechazadaException(Motivo.SIN_FUENTES, todasPorTope
                ? "Todas las fuentes ya recibieron sus " + tope + " lecturas de hoy. Vuelve a intentarlo mañana."
                : "Ninguna conexión se puede leer ahora: están pausadas o ya recibieron sus " + tope
                        + " lecturas de hoy.");
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
