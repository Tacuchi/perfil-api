package net.tacuchi.perfil.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Lo que la persona administra de una fuente. La clave cifrada nunca sale de la API: aquí solo consta cuándo se
 * cargó.
 */
public record Conexion(
        Fuente fuente,
        boolean pausada,
        String motivoPausa,
        BigDecimal topeMensual,
        Instant claveCargadaEn) {

    public static Conexion porDefecto(Fuente fuente) {
        return new Conexion(fuente, false, null, null, null);
    }

    /**
     * Si tiene lo que su fuente necesita. Una de pago sin tope mensual no se lee: nunca gasta sin límite. La sesión
     * vive en la máquina de la persona: se da por iniciada desde su primera lectura correcta hasta que el lector
     * reporta que falta.
     *
     * @param lectura la última lectura de la fuente, o null si nunca se leyó
     */
    public boolean tieneLoQueNecesita(LecturaFuente lectura) {
        return switch (fuente.necesita()) {
            case NADA -> true;
            case CLAVE -> claveCargadaEn != null && topeMensual != null;
            case SESION -> lectura != null && lectura.ultimaCorrecta() != null
                    && FalloMaquina.deDetalle(lectura.detalle()).filter(f -> f == FalloMaquina.SIN_SESION).isEmpty();
        };
    }

    /**
     * @param lectura la última lectura de la fuente, o null si nunca se leyó
     * @param hoy     el día de Lima en curso
     */
    public EstadoConexion estado(LecturaFuente lectura, LocalDate hoy) {
        if (pausada) {
            return EstadoConexion.PAUSADA;
        }
        if (!tieneLoQueNecesita(lectura)) {
            return fuente.necesita() == Requisito.SESION
                    ? EstadoConexion.FALTA_INICIAR_SESION : EstadoConexion.FALTA_CONFIGURAR;
        }
        if (fuente.desdeMaquina() && !leidaEl(lectura, hoy)) {
            return EstadoConexion.PENDIENTE_MAQUINA;
        }
        return EstadoConexion.LISTA;
    }

    private static boolean leidaEl(LecturaFuente lectura, LocalDate dia) {
        return lectura != null && LocalDate.ofInstant(lectura.ultimaLectura(), Reglas.ZONA_PERSONA).equals(dia);
    }
}
