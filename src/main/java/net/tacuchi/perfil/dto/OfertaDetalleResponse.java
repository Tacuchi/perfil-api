package net.tacuchi.perfil.dto;

/** Una oferta con su texto y, si el texto lo trae, el correo de contacto. */
public record OfertaDetalleResponse(OfertaResponse oferta, String descripcion, String correoContacto) {
}
