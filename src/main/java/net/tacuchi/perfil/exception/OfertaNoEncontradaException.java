package net.tacuchi.perfil.exception;

public class OfertaNoEncontradaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OfertaNoEncontradaException(long id) {
        super("No existe la oferta " + id + ".");
    }
}
