package net.tacuchi.perfil.dto;

/**
 * La corrida local que la persona abrió desde el panel. El código es de un solo uso y solo viaja aquí y en el
 * enlace al lector: la API guarda su huella, nunca el código.
 */
public record CorridaAbiertaResponse(String codigo, RecoleccionResponse avance) {
}
