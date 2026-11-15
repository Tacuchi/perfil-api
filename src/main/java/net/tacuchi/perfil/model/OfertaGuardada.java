package net.tacuchi.perfil.model;

import java.time.Instant;
import java.util.List;

/** Una oferta tal como está guardada: lo recogido, su clasificación y lo que decidió la persona. */
public record OfertaGuardada(
        long id,
        Fuente fuente,
        String titulo,
        String empresa,
        Instant publicadaEn,
        String ubicacion,
        Modalidad modalidad,
        String url,
        String descripcion,
        String correoContacto,
        String idioma,
        Encaje encaje,
        List<String> motivos,
        List<String> tecnologias,
        String claveSimilitud,
        EstadoOferta estado,
        Instant postuladaEn,
        boolean publicada,
        Instant vistaPrimera,
        Instant vistaUltima) {

    /** Fecha que se muestra y por la que se filtra: la de publicación, o la primera vez que se vio si la fuente no la da. */
    public Instant fecha() {
        return publicadaEn != null ? publicadaEn : vistaPrimera;
    }
}
