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
    /** Lecturas que puede recibir una fuente en un día de Lima, programadas y a demanda, terminen bien o no. */
    public static final int TOPE_LECTURAS_DIA = 3;
    /** Una fuente sin tope de lecturas por día. */
    public static final int SIN_TOPE_DIARIO = 0;
    /** La recolección programada lee una vez al día cada fuente que ese día aún no tiene lecturas. */
    public static final int LECTURAS_PROGRAMADAS_DIA = 1;
    /** Avisos que admite cada entrega del lector de la máquina: llega cortada, nunca es una lectura completa. */
    public static final int TOPE_AVISOS_ENTREGA = 25;
    /** Plazo para que el lector inicie la corrida que la persona abrió desde el panel. */
    public static final Duration PLAZO_REPORTE_LECTOR = Duration.ofMinutes(2);
    /** Plazo total de una corrida local, con la espera del inicio de sesión incluida. */
    public static final Duration PLAZO_CORRIDA_LOCAL = Duration.ofMinutes(15);
    /** Tope de ofertas que devuelve una lista. */
    public static final int TOPE_LISTA_OFERTAS = 500;

    private Reglas() {
    }
}
