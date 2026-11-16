package net.tacuchi.perfil.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** El tope mensual de gasto de una conexión de pago, en dólares con hasta dos decimales. */
public record TopeRequest(@NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal topeMensual) {
}
