package net.tacuchi.perfil.model;

/**
 * Sitios de los que se recogen ofertas. El nombre visible es el que cada sitio pide citar. Cada uno declara desde
 * dónde se lee y lo que necesita para leerse.
 */
public enum Fuente {
    GET_ON_BOARD("Get on Board", Via.SERVIDOR, Requisito.NADA),
    REMOTIVE("Remotive", Via.SERVIDOR, Requisito.NADA),
    REMOTE_OK("Remote OK", Via.SERVIDOR, Requisito.NADA);

    private final String nombre;
    private final Via via;
    private final Requisito necesita;

    Fuente(String nombre, Via via, Requisito necesita) {
        this.nombre = nombre;
        this.via = via;
        this.necesita = necesita;
    }

    public String nombre() {
        return nombre;
    }

    public Via via() {
        return via;
    }

    public Requisito necesita() {
        return necesita;
    }
}
