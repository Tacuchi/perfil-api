package net.tacuchi.perfil.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Lo que el lector manda para iniciar, empezar una fuente o terminar: el código de la corrida. */
public record CodigoCorridaRequest(@NotNull @Pattern(regexp = CodigoCorridaRequest.FORMATO) String codigo) {

    /** 32 bytes aleatorios en base64url sin relleno. */
    public static final String FORMATO = "[A-Za-z0-9_-]{43}";
}
