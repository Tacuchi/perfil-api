package net.tacuchi.perfil.dto;

import net.tacuchi.perfil.model.Fuente;

/** Referencia a otra oferta, de otra fuente, que parece la misma. */
public record RepetidaResponse(long id, Fuente fuente, String fuenteNombre) {
}
