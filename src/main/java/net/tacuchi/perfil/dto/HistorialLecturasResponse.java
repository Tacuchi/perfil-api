package net.tacuchi.perfil.dto;

import java.util.List;

public record HistorialLecturasResponse(List<LecturaHistorial> lecturas, boolean hayMas) { }
