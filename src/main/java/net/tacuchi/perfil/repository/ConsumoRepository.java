package net.tacuchi.perfil.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Reglas;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lo que consumió cada conexión de pago, en dólares, por mes de Lima. El mes se identifica por su primer día. */
@Repository
@RequiredArgsConstructor
public class ConsumoRepository {

    private final JdbcClient jdbc;

    /** El mes de Lima al que pertenece ese instante. */
    public static LocalDate mesDe(Instant instante) {
        return LocalDate.ofInstant(instante, Reglas.ZONA_PERSONA).withDayOfMonth(1);
    }

    /** Suma el consumo de una corrida al mes. Una sola sentencia: dos sumas a la vez no se pisan. */
    public void sumar(Fuente fuente, LocalDate mes, BigDecimal consumo) {
        jdbc.sql("""
                INSERT INTO consumo_mes (fuente, mes, consumo) VALUES (:fuente, :mes, :consumo)
                ON CONFLICT (fuente, mes) DO UPDATE SET consumo = consumo_mes.consumo + EXCLUDED.consumo""")
                .param("fuente", fuente.name())
                .param("mes", mes)
                .param("consumo", consumo)
                .update();
    }

    public BigDecimal delMes(Fuente fuente, LocalDate mes) {
        return jdbc.sql("SELECT consumo FROM consumo_mes WHERE fuente = :fuente AND mes = :mes")
                .param("fuente", fuente.name())
                .param("mes", mes)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }
}
