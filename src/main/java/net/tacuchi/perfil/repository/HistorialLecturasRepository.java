package net.tacuchi.perfil.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.dto.HistorialLecturasResponse;
import net.tacuchi.perfil.dto.LecturaHistorial;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class HistorialLecturasRepository {
    private final JdbcClient jdbc;

    public void registrar(Fuente fuente, Instant inicio, long duracionMs, EstadoLectura estado,
            int leidas, int nuevas, BigDecimal costo, String motivo) {
        jdbc.sql("""
                INSERT INTO lectura_historial (fuente, inicio, duracion_ms, estado, parcial, leidas, nuevas, costo_usd, motivo)
                VALUES (:fuente, :inicio, :duracion, :estado, :parcial, :leidas, :nuevas, :costo, :motivo)
                """)
                .param("fuente", fuente.name()).param("inicio", Timestamp.from(inicio))
                .param("duracion", duracionMs).param("estado", estado.name()).param("parcial", estado.parcial(motivo))
                .param("leidas", leidas).param("nuevas", nuevas).param("costo", costo).param("motivo", motivo).update();
    }

    public HistorialLecturasResponse listar(Fuente fuente, Long antesDe) {
        var filas = jdbc.sql("""
                SELECT * FROM lectura_historial
                WHERE fuente = :fuente AND (CAST(:antes AS BIGINT) IS NULL OR (inicio, id) < (
                    SELECT inicio, id FROM lectura_historial WHERE id = :antes AND fuente = :fuente))
                ORDER BY inicio DESC, id DESC LIMIT 21
                """)
                .param("fuente", fuente.name()).param("antes", antesDe)
                .query((rs, n) -> new LecturaHistorial(rs.getLong("id"), rs.getTimestamp("inicio").toInstant(),
                        rs.getLong("duracion_ms"), EstadoLectura.valueOf(rs.getString("estado")),
                        rs.getBoolean("parcial"), rs.getInt("leidas"), rs.getInt("nuevas"),
                        rs.getBigDecimal("costo_usd"), rs.getString("motivo"))).list();
        return new HistorialLecturasResponse(filas.stream().limit(20).toList(), filas.size() > 20);
    }
}
