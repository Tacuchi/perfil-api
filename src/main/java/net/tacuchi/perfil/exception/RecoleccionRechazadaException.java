package net.tacuchi.perfil.exception;

import java.util.List;
import net.tacuchi.perfil.dto.FuenteOmitida;
import net.tacuchi.perfil.model.MotivoOmision;

/** Un lanzamiento que no se acepta. El mensaje es para la persona y no lleva detalle interno. */
public class RecoleccionRechazadaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Motivo {
        EN_CURSO, TOPE_DIARIO, PAUSADA, SIN_FUENTES, DESDE_MAQUINA, FALTA_CONFIGURAR, TOPE_MENSUAL
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
            case TOPE_DIARIO -> new RecoleccionRechazadaException(Motivo.TOPE_DIARIO, omitida.nombre()
                    + (tope == 1 ? " ya recibió su lectura de hoy." : " ya recibió sus " + tope + " lecturas de hoy.")
                    + " Vuelve a intentarlo mañana.");
            case PAUSADA -> new RecoleccionRechazadaException(Motivo.PAUSADA,
                    omitida.nombre() + " está pausada. Reanúdala en Conexiones para leerla.");
            case DESDE_MAQUINA -> new RecoleccionRechazadaException(Motivo.DESDE_MAQUINA,
                    omitida.nombre() + " se lee desde tu máquina. Usa «Leer desde mi máquina» en Conexiones.");
            case FALTA_CONFIGURAR -> new RecoleccionRechazadaException(Motivo.FALTA_CONFIGURAR,
                    omitida.nombre() + " no está configurada. Completa lo que le falta en Conexiones para leerla.");
            case TOPE_MENSUAL -> new RecoleccionRechazadaException(Motivo.TOPE_MENSUAL,
                    omitida.nombre() + " llegó a su tope del mes. Súbelo en Conexiones o espera al mes siguiente.");
        };
    }

    /**
     * Ninguna fuente del lanzamiento general se puede leer ahora. Las conexiones de la máquina no cuentan: nunca se
     * leen por esta vía; tampoco las que nunca se configuraron.
     */
    public static RecoleccionRechazadaException sinFuentes(List<FuenteOmitida> omitidas, int tope) {
        boolean todasPorTope = omitidas.stream()
                .filter(o -> o.motivo() != MotivoOmision.DESDE_MAQUINA && o.motivo() != MotivoOmision.FALTA_CONFIGURAR)
                .allMatch(o -> o.motivo() == MotivoOmision.TOPE_DIARIO);
        return new RecoleccionRechazadaException(Motivo.SIN_FUENTES, todasPorTope
                ? "Todas las fuentes ya recibieron sus " + tope + " lecturas de hoy. Vuelve a intentarlo mañana."
                : "Ninguna conexión se puede leer ahora: están pausadas, sin configurar, en su tope del mes o ya "
                        + "recibieron sus " + tope + " lecturas de hoy.");
    }

    /** Ninguna conexión de la máquina se puede leer ahora: la corrida local no se abre. */
    public static RecoleccionRechazadaException sinConexionesDeMaquina() {
        return new RecoleccionRechazadaException(Motivo.SIN_FUENTES,
                "Ninguna conexión de tu máquina se puede leer ahora: están pausadas o ya recibieron sus lecturas de hoy.");
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
