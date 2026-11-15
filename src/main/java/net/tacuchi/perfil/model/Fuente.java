package net.tacuchi.perfil.model;

/** Sitios de los que se recogen ofertas. El nombre visible es el que cada sitio pide citar. */
public enum Fuente {
    GET_ON_BOARD("Get on Board"),
    REMOTIVE("Remotive"),
    REMOTE_OK("Remote OK");

    private final String nombre;

    Fuente(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }
}
