package net.tacuchi.perfil.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import net.tacuchi.perfil.model.FalloMaquina;
import net.tacuchi.perfil.model.Reglas;

/**
 * Lo que el lector entrega de una fuente: sus avisos, cortados por el tope de cada entrega, o el fallo
 * clasificado. Una entrega sin avisos ni fallo es una lectura vacía.
 */
public record EntregaRequest(
        @NotNull @Pattern(regexp = CodigoCorridaRequest.FORMATO) String codigo,
        @Valid @Size(max = Reglas.TOPE_AVISOS_ENTREGA, message = "admite hasta {max} avisos por entrega")
        List<OfertaEntregada> ofertas,
        FalloMaquina fallo) {

    public List<OfertaEntregada> avisos() {
        return ofertas == null ? List.of() : ofertas;
    }

    @AssertTrue(message = "trae avisos o un fallo, no los dos")
    public boolean isAvisosOFallo() {
        return fallo == null || avisos().isEmpty();
    }
}
