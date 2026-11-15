package net.tacuchi.perfil.model;

/** Reglas del historial y de la carga del CV. */
public final class Reglas {

    public static final int TOPE_VERSIONES = 10;
    public static final long TAMANO_MAXIMO_BYTES = 10L * 1024 * 1024;
    public static final String TAMANO_MAXIMO_TEXTO = "10 MB";

    private Reglas() {
    }
}
