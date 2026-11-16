package net.tacuchi.perfil.dto;

import java.time.Instant;
import java.util.List;
import net.tacuchi.perfil.model.Fuente;

/** Lo que el lector debe leer en esta corrida y hasta cuándo puede entregarlo. */
public record CorridaLectorResponse(List<FuenteALeer> fuentes, Instant venceEn) {

    /** Una conexión de la máquina que el lector debe leer, con los avisos que admite cada entrega. */
    public record FuenteALeer(Fuente fuente, String nombre, int avisosMaximos) {

        public static FuenteALeer de(Fuente fuente) {
            return new FuenteALeer(fuente, fuente.nombre(), fuente.avisosPorEntrega());
        }
    }
}
