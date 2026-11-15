package net.tacuchi.perfil.model;

import java.time.Duration;
import java.time.ZoneId;

/** Reglas del historial del CV y del monitor de ofertas. */
public final class Reglas {

    public static final int TOPE_VERSIONES = 10;
    public static final long TAMANO_MAXIMO_BYTES = 10L * 1024 * 1024;
    public static final String TAMANO_MAXIMO_TEXTO = "10 MB";

    /** Nuevas y descartadas se eliminan pasado este plazo desde la última vez que se vieron. */
    public static final Duration CONSERVACION_OFERTAS = Duration.ofDays(60);
    /** El día de la persona: la lectura diaria y el correo diario se cuentan en esta zona. */
    public static final ZoneId ZONA_PERSONA = ZoneId.of("America/Lima");
    /** Tope de ofertas que devuelve una lista. */
    public static final int TOPE_LISTA_OFERTAS = 500;

    private Reglas() {
    }
}
