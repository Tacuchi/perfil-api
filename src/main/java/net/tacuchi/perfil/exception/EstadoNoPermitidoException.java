package net.tacuchi.perfil.exception;

/** La persona solo marca ofertas como guardadas, descartadas o postuladas; «nueva» la pone la recolección. */
public class EstadoNoPermitidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EstadoNoPermitidoException(String recibido) {
        super("estado: se espera GUARDADA, DESCARTADA o POSTULADA; llegó " + recibido + ".");
    }
}
