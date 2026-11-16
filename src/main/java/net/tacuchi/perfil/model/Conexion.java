package net.tacuchi.perfil.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Lo que la persona administra de una fuente. La clave cifrada nunca sale de la API: aquí solo consta cuándo se
 * cargó. El tope mensual es de gasto, en dólares, y solo lo usan las de pago.
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

    public static final String FALTA_LLAVE = "falta la llave de claves del servidor";
    public static final String FALTA_CLAVE = "falta la clave del servicio";
    public static final String FALTA_TOPE = "falta el tope mensual";

    /**
     * Lo que le falta a una de pago para leerse, en el orden en que la persona lo resuelve; vacío si no le falta
     * nada o si no es de pago. Sin la llave del servidor no se puede cifrar ni descifrar ninguna clave.
     */
    public Optional<String> queFalta(boolean llaveDisponible) {
        if (fuente.necesita() != Requisito.CLAVE) {
            return Optional.empty();
        }
        if (!llaveDisponible) {
            return Optional.of(FALTA_LLAVE);
        }
        if (claveCargadaEn == null) {
            return Optional.of(FALTA_CLAVE);
        }
        return topeMensual == null ? Optional.of(FALTA_TOPE) : Optional.empty();
    }

    /** Si una de pago ya consumió su tope del mes. Sin tope no se lee, pero eso es «falta configurar». */
    public boolean topeAlcanzado(BigDecimal consumoMes) {
        return topeMensual != null && consumoMes.compareTo(topeMensual) >= 0;
    }

    /** Lo que queda del tope del mes; cero si ya se alcanzó o no hay tope. */
    public BigDecimal saldo(BigDecimal consumoMes) {
        return topeMensual == null ? BigDecimal.ZERO : topeMensual.subtract(consumoMes).max(BigDecimal.ZERO);
    }

    /**
     * Si tiene lo que su fuente necesita. Una de pago sin clave, sin tope mensual o sin la llave del servidor no se
     * lee: nunca gasta sin límite. La sesión vive en la máquina de la persona: se da por iniciada desde su primera
     * lectura correcta hasta que el lector reporta que falta.
     *
     * @param lectura la última lectura de la fuente, o null si nunca se leyó
     */
    public boolean tieneLoQueNecesita(LecturaFuente lectura, boolean llaveDisponible) {
        return switch (fuente.necesita()) {
            case NADA -> true;
            case CLAVE -> queFalta(llaveDisponible).isEmpty();
            case SESION -> lectura != null && lectura.ultimaCorrecta() != null
                    && FalloMaquina.deDetalle(lectura.detalle()).filter(f -> f == FalloMaquina.SIN_SESION).isEmpty();
        };
    }

    /**
     * @param lectura    la última lectura de la fuente, o null si nunca se leyó
     * @param hoy        el día de Lima en curso
     * @param consumoMes lo consumido en el mes de Lima en curso (cero si no es de pago)
     */
    public EstadoConexion estado(LecturaFuente lectura, LocalDate hoy, boolean llaveDisponible,
            BigDecimal consumoMes) {
        if (pausada) {
            return EstadoConexion.PAUSADA;
        }
        if (!tieneLoQueNecesita(lectura, llaveDisponible)) {
            return fuente.necesita() == Requisito.SESION
                    ? EstadoConexion.FALTA_INICIAR_SESION : EstadoConexion.FALTA_CONFIGURAR;
        }
        if (fuente.dePago() && topeAlcanzado(consumoMes)) {
            return EstadoConexion.TOPE_MENSUAL;
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
