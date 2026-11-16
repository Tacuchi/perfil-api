package net.tacuchi.perfil.model;

/**
 * Sitios de los que se recogen ofertas. El nombre visible es el que cada sitio pide citar. Cada uno declara desde
 * dónde se lee, lo que necesita para leerse y cuántas lecturas admite por día de Lima.
 */
public enum Fuente {
    GET_ON_BOARD("Get on Board", Via.SERVIDOR, Requisito.NADA, Reglas.TOPE_LECTURAS_DIA),
    REMOTIVE("Remotive", Via.SERVIDOR, Requisito.NADA, Reglas.TOPE_LECTURAS_DIA),
    REMOTE_OK("Remote OK", Via.SERVIDOR, Requisito.NADA, Reglas.TOPE_LECTURAS_DIA),
    LINKEDIN("LinkedIn", Via.PAGO, Requisito.CLAVE, Reglas.SIN_TOPE_DIARIO),
    BUMERAN("Bumeran", Via.PAGO, Requisito.CLAVE, Reglas.SIN_TOPE_DIARIO),
    COMPUTRABAJO("Computrabajo", Via.PAGO, Requisito.CLAVE, Reglas.SIN_TOPE_DIARIO);

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

    /** Lecturas que admite en un día de Lima, terminen bien o no; {@link Reglas#SIN_TOPE_DIARIO} si no tiene tope. */
    public int lecturasDia() {
        return lecturasDia;
    }

    /** Las de pago no tienen tope diario: las acota su tope mensual de gasto. */
    public boolean conTopeDiario() {
        return lecturasDia != Reglas.SIN_TOPE_DIARIO;
    }

    public boolean dePago() {
        return via == Via.PAGO;
    }

    public boolean desdeMaquina() {
        return via == Via.MAQUINA;
    }

    /** Avisos que admite una entrega del lector; null si no se lee desde la máquina. */
    public Integer avisosPorEntrega() {
        return desdeMaquina() ? Reglas.TOPE_AVISOS_ENTREGA : null;
    }
}
