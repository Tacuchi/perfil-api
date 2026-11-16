package net.tacuchi.perfil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Modalidad;
import net.tacuchi.perfil.model.OfertaRecogida;

/**
 * Un aviso tal como lo entrega el lector de la máquina, ya convertido. Los campos que el sitio no informa van en
 * null. Los topes son los de la tabla de ofertas.
 */
public record OfertaEntregada(
        @NotBlank @Size(max = 300) String idExterno,
        @NotBlank @Size(max = 500) String titulo,
        @Size(max = 300) String empresa,
        Instant publicadaEn,
        @Size(max = 500) String ubicacion,
        Modalidad modalidad,
        Boolean ubicacionAdmite,
        Boolean nivelJunior,
        @Pattern(regexp = "es|en") String idioma,
        @NotNull @Size(max = 100_000) String descripcion,
        @NotBlank @Size(max = 2000) @Pattern(regexp = "https://\\S+", message = "debe ser una URL https") String url) {

    public OfertaRecogida comoRecogida(Fuente fuente) {
        return new OfertaRecogida(fuente, idExterno, titulo, empresa, publicadaEn, ubicacion,
                modalidad == null ? Modalidad.NO_INFORMA : modalidad, ubicacionAdmite, nivelJunior, idioma,
                descripcion, url, null);
    }
}
