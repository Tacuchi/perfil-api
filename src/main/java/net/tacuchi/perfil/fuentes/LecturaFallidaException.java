package net.tacuchi.perfil.fuentes;

import net.tacuchi.perfil.model.EstadoLectura;

/** Una fuente que falló o bloqueó la lectura. El detalle es corto y sin datos de la respuesta. */
public class LecturaFallidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final EstadoLectura estado;
    private java.math.BigDecimal costoUsd;

    public java.math.BigDecimal getCostoUsd() { return costoUsd; }

    public LecturaFallidaException(EstadoLectura estado, String detalle, java.math.BigDecimal costoUsd) {
        this(estado, detalle);
        this.costoUsd = costoUsd;
    }

    public LecturaFallidaException(EstadoLectura estado, String detalle) {
        super(detalle);
        this.estado = estado;
    }

    public LecturaFallidaException(EstadoLectura estado, String detalle, Throwable causa) {
        super(detalle, causa);
        this.estado = estado;
    }

    public EstadoLectura getEstado() {
        return estado;
    }
}
