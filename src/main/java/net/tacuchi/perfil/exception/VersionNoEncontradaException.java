package net.tacuchi.perfil.exception;

public class VersionNoEncontradaException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final String DESCRIPTION = "Versión no encontrada";

    public VersionNoEncontradaException(String detalle) {
        super(DESCRIPTION + ": " + detalle);
    }
}
