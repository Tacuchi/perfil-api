package net.tacuchi.perfil.repository;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.model.Fuente;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Cuántas lecturas recibió cada fuente en cada día de Lima. Una lectura se cuenta antes de pedirla al sitio. */
@Repository
@RequiredArgsConstructor
public class LecturaDiaRepository {

    private final JdbcClient jdbc;

    /**
     * Cuenta una lectura de la fuente en ese día si el día lleva menos de {@code tope}. Una sola sentencia: dos
     * hilos que reservan a la vez nunca pasan el tope.
     *
     * @return false si la fuente ya llegó al tope ese día
     */
    public boolean reservar(Fuente fuente, LocalDate dia, int tope) {
        return jdbc.sql("""
                INSERT INTO lectura_dia (fuente, dia, lecturas) VALUES (:fuente, :dia, 1)
                ON CONFLICT (fuente, dia) DO UPDATE SET lecturas = lectura_dia.lecturas + 1
                WHERE lectura_dia.lecturas < :tope""")
                .param("fuente", fuente.name())
                .param("dia", dia)
                .param("tope", tope)
                .update() == 1;
    }

    /** Lecturas de cada fuente en ese día; una fuente sin lecturas no aparece. */
    public Map<Fuente, Integer> delDia(LocalDate dia) {
        return jdbc.sql("SELECT fuente, lecturas FROM lectura_dia WHERE dia = :dia")
                .param("dia", dia)
                .query((rs, n) -> Map.entry(Fuente.valueOf(rs.getString("fuente")), rs.getInt("lecturas")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum,
                        () -> new EnumMap<>(Fuente.class)));
    }
}
