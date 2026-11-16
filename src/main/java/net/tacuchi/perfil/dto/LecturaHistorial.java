package net.tacuchi.perfil.dto;

import java.math.BigDecimal;
import java.time.Instant;
import net.tacuchi.perfil.model.EstadoLectura;

public record LecturaHistorial(long id, Instant inicio, long duracionMs, EstadoLectura estado,
        boolean parcial, int leidas, int nuevas, BigDecimal costoUsd, String motivo) { }
