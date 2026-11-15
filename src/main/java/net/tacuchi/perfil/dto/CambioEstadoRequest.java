package net.tacuchi.perfil.dto;

import jakarta.validation.constraints.NotNull;
import net.tacuchi.perfil.model.EstadoOferta;

public record CambioEstadoRequest(@NotNull(message = "es obligatorio: GUARDADA, DESCARTADA o POSTULADA") EstadoOferta estado) {
}
