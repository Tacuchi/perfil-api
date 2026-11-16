package net.tacuchi.perfil.model;

import java.math.BigDecimal;
import java.time.Instant;

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

    /** Si tiene lo que su fuente necesita. Una de pago sin tope mensual no se lee: nunca gasta sin límite. */
    public boolean tieneLoQueNecesita() {
        return switch (fuente.necesita()) {
            case NADA -> true;
            case CLAVE -> claveCargadaEn != null && topeMensual != null;
            // La sesión vive en la máquina de la persona: se sabe que la tiene por su última lectura correcta.
            case SESION -> false;
        };
    }

    public EstadoConexion estado() {
        if (pausada) {
            return EstadoConexion.PAUSADA;
        }
        return tieneLoQueNecesita() ? EstadoConexion.LISTA : EstadoConexion.FALTA_CONFIGURAR;
    }
}
