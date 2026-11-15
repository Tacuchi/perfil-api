package net.tacuchi.perfil.exception;

/** Un lanzamiento a demanda que no se acepta. El mensaje es para la persona y no lleva detalle interno. */
public class RecoleccionRechazadaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Motivo {
        EN_CURSO, TOPE_DIARIO, SIN_FUENTES
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

    public static RecoleccionRechazadaException topeDiario(String fuente, int tope) {
        return new RecoleccionRechazadaException(Motivo.TOPE_DIARIO,
                fuente + " ya recibió sus " + tope + " lecturas de hoy. Vuelve a intentarlo mañana.");
    }

    public static RecoleccionRechazadaException sinFuentes(int tope) {
        return new RecoleccionRechazadaException(Motivo.SIN_FUENTES,
                "Todas las fuentes ya recibieron sus " + tope + " lecturas de hoy. Vuelve a intentarlo mañana.");
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
