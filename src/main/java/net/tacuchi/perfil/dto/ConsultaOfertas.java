package net.tacuchi.perfil.dto;

import java.time.LocalDate;
import java.util.List;
import net.tacuchi.perfil.model.Encaje;
import net.tacuchi.perfil.model.EstadoOferta;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Modalidad;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Filtros de la lista, por query string (`?encaje=ALTO&encaje=MEDIO&fuente=REMOTIVE...`). Sin encaje ni estado,
 * la lista es la de por defecto: nuevas de encaje alto y medio. Las fechas son días de Lima, ambos incluidos.
 */
public record ConsultaOfertas(
        List<Encaje> encaje,
        List<EstadoOferta> estado,
        List<Fuente> fuente,
        List<Modalidad> modalidad,
        String tecnologia,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
}
