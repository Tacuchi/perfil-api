package net.tacuchi.perfil.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo fijo de fallos que el lector de la máquina reporta por fuente. El detalle lo escribe la API, nunca el
 * lector: así el estado de la conexión se deduce de un texto propio.
 *
 * <ul>
 *   <li>SIN_SESION: no hay sesión iniciada; no gasta la lectura del día.</li>
 *   <li>VERIFICACION y RITMO: el sitio pidió una verificación o frenó el ritmo; la conexión queda pausada.</li>
 *   <li>VACIA: la lectura no trajo respuestas; es un fallo, nunca un día sin ofertas.</li>
 * </ul>
 */
public enum FalloMaquina {
    SIN_SESION(EstadoLectura.BLOQUEO, "falta iniciar sesión", false),
    VERIFICACION(EstadoLectura.BLOQUEO, "el sitio pidió una verificación", true),
    RITMO(EstadoLectura.BLOQUEO, "el sitio limitó el ritmo de lectura", true),
    VACIA(EstadoLectura.FALLO, "la lectura no trajo respuestas", false);

    private final EstadoLectura estado;
    private final String detalle;
    private final boolean pausa;

    FalloMaquina(EstadoLectura estado, String detalle, boolean pausa) {
        this.estado = estado;
        this.detalle = detalle;
        this.pausa = pausa;
    }

    public EstadoLectura estado() {
        return estado;
    }

    public String detalle() {
        return detalle;
    }

    /** Si deja la conexión pausada hasta que la persona la reanude. */
    public boolean pausa() {
        return pausa;
    }

    public static Optional<FalloMaquina> deDetalle(String detalle) {
        return Arrays.stream(values()).filter(f -> f.detalle.equals(detalle)).findFirst();
    }
}
