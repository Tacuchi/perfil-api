package net.tacuchi.perfil.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.LecturaFuente;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Última lectura de cada fuente. La fecha de la última lectura correcta solo avanza con una lectura correcta. */
@Repository
@RequiredArgsConstructor
public class FuenteRepository {

    private final JdbcClient jdbc;

    public void registrar(Fuente fuente, EstadoLectura estado, String detalle, Instant cuando, int ofertasLeidas) {
        jdbc.sql("""
                INSERT INTO fuente (nombre, estado, detalle, ultima_lectura, ultima_correcta, ofertas_leidas)
                VALUES (:nombre, :estado, :detalle, :cuando,
                        CASE WHEN :estado = 'CORRECTA' THEN CAST(:cuando AS TIMESTAMPTZ) END, :ofertas)
                ON CONFLICT (nombre) DO UPDATE SET
                    estado = EXCLUDED.estado, detalle = EXCLUDED.detalle, ultima_lectura = EXCLUDED.ultima_lectura,
                    ultima_correcta = COALESCE(EXCLUDED.ultima_correcta, fuente.ultima_correcta),
                    ofertas_leidas = EXCLUDED.ofertas_leidas""")
                .param("nombre", fuente.name())
                .param("estado", estado.name())
                .param("detalle", detalle)
                .param("cuando", Timestamp.from(cuando))
                .param("ofertas", ofertasLeidas)
                .update();
    }

    public Optional<LecturaFuente> buscar(Fuente fuente) {
        return jdbc.sql("SELECT * FROM fuente WHERE nombre = :nombre")
                .param("nombre", fuente.name())
                .query((rs, n) -> {
                    Timestamp correcta = rs.getTimestamp("ultima_correcta");
                    return new LecturaFuente(
                            Fuente.valueOf(rs.getString("nombre")),
                            EstadoLectura.valueOf(rs.getString("estado")),
                            rs.getString("detalle"),
                            rs.getTimestamp("ultima_lectura").toInstant(),
                            correcta == null ? null : correcta.toInstant(),
                            rs.getInt("ofertas_leidas"));
                })
                .optional();
    }

    public List<LecturaFuente> todas() {
        return Arrays.stream(Fuente.values()).map(this::buscar).flatMap(Optional::stream).toList();
    }
}
