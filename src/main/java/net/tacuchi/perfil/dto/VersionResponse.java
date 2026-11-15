package net.tacuchi.perfil.dto;

import java.time.Instant;

public record VersionResponse(String id, Instant fecha, boolean conWord, boolean vigente) {
}
