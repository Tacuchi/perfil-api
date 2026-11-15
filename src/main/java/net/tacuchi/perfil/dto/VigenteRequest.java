package net.tacuchi.perfil.dto;

import jakarta.validation.constraints.NotBlank;

public record VigenteRequest(@NotBlank(message = "indica la versión que queda vigente") String version) {
}
