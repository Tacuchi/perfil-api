package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import net.tacuchi.perfil.soporte.BaseDeDatosDePrueba;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** La migración del conteo diario sobre una base que ya tiene datos del esquema anterior. */
class MigracionLecturasTest {

    @Test
    void siembraUnaLecturaEnElDiaDeLimaDeLaUltimaLecturaSinTocarLoDemas() throws SQLException {
        String esquema = "migracion_" + System.nanoTime();
        Flyway.configure()
                .dataSource(BaseDeDatosDePrueba.url(), BaseDeDatosDePrueba.usuario(), BaseDeDatosDePrueba.clave())
                .schemas(esquema).target("1").load().migrate();
        try (Connection conexion = conectar(); Statement sql = conexion.createStatement()) {
            sql.execute("SET search_path TO " + esquema);
            // 03:30 UTC del 15 son las 22:30 del 14 en Lima; 12:00 UTC del 15 es el 15 en Lima.
            sql.execute("""
                    INSERT INTO fuente (nombre, estado, detalle, ultima_lectura, ultima_correcta, ofertas_leidas) VALUES
                    ('REMOTIVE', 'CORRECTA', NULL, '2026-11-15T03:30:00Z', '2026-11-15T03:30:00Z', 4),
                    ('GET_ON_BOARD', 'FALLO', 'el sitio respondió HTTP 422', '2026-11-15T12:00:00Z', NULL, 0)""");
            sql.execute("INSERT INTO aviso (dia, enviado_en, ofertas) VALUES ('2026-11-14', '2026-11-15T03:31:00Z', 2)");
        }

        Flyway.configure()
                .dataSource(BaseDeDatosDePrueba.url(), BaseDeDatosDePrueba.usuario(), BaseDeDatosDePrueba.clave())
                .schemas(esquema).load().migrate();

        try (Connection conexion = conectar(); Statement sql = conexion.createStatement()) {
            sql.execute("SET search_path TO " + esquema);
            Map<String, String> sembradas = new LinkedHashMap<>();
            try (ResultSet filas = sql.executeQuery("SELECT fuente, dia, lecturas FROM lectura_dia ORDER BY fuente")) {
                while (filas.next()) {
                    sembradas.put(filas.getString("fuente"), filas.getString("dia") + "×" + filas.getInt("lecturas"));
                }
            }
            assertThat(sembradas).containsExactly(
                    Map.entry("GET_ON_BOARD", "2026-11-15×1"),
                    Map.entry("REMOTIVE", "2026-11-14×1"));
            try (ResultSet filas = sql.executeQuery("SELECT count(*) FROM fuente")) {
                filas.next();
                assertThat(filas.getInt(1)).isEqualTo(2);
            }
            try (ResultSet filas = sql.executeQuery("SELECT ofertas FROM aviso")) {
                filas.next();
                assertThat(filas.getInt(1)).isEqualTo(2);
            }
        }
    }

    private static Connection conectar() throws SQLException {
        return DriverManager.getConnection(BaseDeDatosDePrueba.url(), BaseDeDatosDePrueba.usuario(),
                BaseDeDatosDePrueba.clave());
    }
}
