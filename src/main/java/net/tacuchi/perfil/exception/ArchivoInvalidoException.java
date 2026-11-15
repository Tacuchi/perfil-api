package net.tacuchi.perfil.exception;

import lombok.Getter;

/** Un archivo de la carga no cumple una regla. Dice cuál archivo y cuál regla. */
@Getter
public class ArchivoInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Motivo { FALTA, TIPO, TAMANO }

    private final String campo;
    private final Motivo motivo;

    public ArchivoInvalidoException(String campo, Motivo motivo, String mensaje) {
        super(mensaje);
        this.campo = campo;
        this.motivo = motivo;
    }
}
