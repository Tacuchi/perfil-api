package net.tacuchi.perfil.exception;

/**
 * Una petición del lector que no corresponde a la corrida abierta. El mensaje nunca lleva el código recibido.
 *
 * <ul>
 *   <li>CODIGO: no hay corrida abierta con ese código (inventado, de otra corrida, ya usado o vencido).</li>
 *   <li>PASO: el código es válido, pero el paso no corresponde (fuente fuera de la corrida, sin empezar o ya
 *       entregada, corrida ya iniciada).</li>
 * </ul>
 */
public class CorridaRechazadaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Motivo {
        CODIGO, PASO
    }

    private final Motivo motivo;

    private CorridaRechazadaException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public static CorridaRechazadaException codigo() {
        return new CorridaRechazadaException(Motivo.CODIGO,
                "No hay una corrida abierta con ese código: no existe, ya terminó o venció.");
    }

    public static CorridaRechazadaException paso(String mensaje) {
        return new CorridaRechazadaException(Motivo.PASO, mensaje);
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
