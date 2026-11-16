package net.tacuchi.perfil.model;

/** Cómo terminó la última lectura de una fuente. */
public enum EstadoLectura {
    CORRECTA, FALLO, BLOQUEO, SIN_LEER;

    /** Se conservaron avisos pero quedaron búsquedas o cargos con advertencias. */
    public boolean parcial(String detalle) {
        return this == CORRECTA && detalle != null && !detalle.isBlank();
    }
}
