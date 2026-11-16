package net.tacuchi.perfil.repository;

import java.sql.Timestamp;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.model.Conexion;
import net.tacuchi.perfil.model.Fuente;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lo que la persona administra de cada conexión. Una fuente sin fila tiene la conexión por defecto. */
@Repository
@RequiredArgsConstructor
public class ConexionRepository {

    private final JdbcClient jdbc;

    public Conexion buscar(Fuente fuente) {
        return todas().get(fuente);
    }

    /** Una conexión por cada fuente conocida, guardada o por defecto. */
    public Map<Fuente, Conexion> todas() {
        Map<Fuente, Conexion> guardadas = jdbc.sql("""
                SELECT fuente, pausada, motivo_pausa, tope_mensual, clave_cargada_en FROM conexion""")
                .query((rs, n) -> {
                    Timestamp cargada = rs.getTimestamp("clave_cargada_en");
                    return new Conexion(
                            Fuente.valueOf(rs.getString("fuente")),
                            rs.getBoolean("pausada"),
                            rs.getString("motivo_pausa"),
                            rs.getBigDecimal("tope_mensual"),
                            cargada == null ? null : cargada.toInstant());
                })
                .list().stream()
                .collect(Collectors.toMap(Conexion::fuente, c -> c));
        Map<Fuente, Conexion> todas = new EnumMap<>(Fuente.class);
        for (Fuente fuente : Fuente.values()) {
            todas.put(fuente, guardadas.getOrDefault(fuente, Conexion.porDefecto(fuente)));
        }
        return todas;
    }

    public Set<Fuente> pausadas() {
        return todas().values().stream().filter(Conexion::pausada).map(Conexion::fuente)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Pausa o reanuda. Reanudar borra el motivo de la pausa. */
    public void pausar(Fuente fuente, boolean pausada, String motivo) {
        jdbc.sql("""
                INSERT INTO conexion (fuente, pausada, motivo_pausa) VALUES (:fuente, :pausada, :motivo)
                ON CONFLICT (fuente) DO UPDATE SET pausada = EXCLUDED.pausada, motivo_pausa = EXCLUDED.motivo_pausa""")
                .param("fuente", fuente.name())
                .param("pausada", pausada)
                .param("motivo", pausada ? motivo : null)
                .update();
    }
}
