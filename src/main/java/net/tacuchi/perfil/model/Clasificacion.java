package net.tacuchi.perfil.model;

import java.util.List;

/** Resultado de clasificar una oferta: el encaje, por qué, y las tecnologías del CV que menciona. */
public record Clasificacion(Encaje encaje, List<String> motivos, List<String> tecnologias) {
}
