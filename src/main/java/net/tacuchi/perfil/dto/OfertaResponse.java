package net.tacuchi.perfil.dto;

import java.time.Instant;
import java.util.List;
import net.tacuchi.perfil.model.Encaje;
import net.tacuchi.perfil.model.EstadoOferta;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Modalidad;

/** Una oferta en la lista. {@code fuenteNombre} es el nombre que la fuente pide citar. */
public record OfertaResponse(
        long id,
        Fuente fuente,
        String fuenteNombre,
        String titulo,
        String empresa,
        Instant fecha,
        String ubicacion,
        Modalidad modalidad,
        String url,
        Encaje encaje,
        List<String> motivos,
        List<String> tecnologias,
        EstadoOferta estado,
        Instant postuladaEn,
        boolean publicada,
        boolean conCorreo,
        List<RepetidaResponse> posiblesRepetidas) {
}
