package net.tacuchi.perfil.model;

/**
 * Sitios de los que se recogen ofertas. El nombre visible es el que cada sitio pide citar. Cada uno declara desde
 * dónde se lee, lo que necesita para leerse y cuántas lecturas admite por día de Lima.
 */
public enum Fuente {
    GET_ON_BOARD("Get on Board", Via.SERVIDOR, Requisito.NADA, Reglas.TOPE_LECTURAS_DIA),
    REMOTIVE("Remotive", Via.SERVIDOR, Requisito.NADA, Reglas.TOPE_LECTURAS_DIA),
    REMOTE_OK("Remote OK", Via.SERVIDOR, Requisito.NADA, Reglas.TOPE_LECTURAS_DIA),
    LINKEDIN("LinkedIn", Via.MAQUINA, Requisito.SESION, 1);

    private final String nombre;
    private final Via via;
    private final Requisito necesita;
    private final int lecturasDia;

    Fuente(String nombre, Via via, Requisito necesita, int lecturasDia) {
        this.nombre = nombre;
        this.via = via;
        this.necesita = necesita;
        this.lecturasDia = lecturasDia;
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

    /** Lecturas que admite en un día de Lima, terminen bien o no. */
    public int lecturasDia() {
        return lecturasDia;
    }

    public boolean desdeMaquina() {
        return via == Via.MAQUINA;
    }

    /** Avisos que admite una entrega del lector; null si no se lee desde la máquina. */
    public Integer avisosPorEntrega() {
        return desdeMaquina() ? Reglas.TOPE_AVISOS_ENTREGA : null;
    }
}
