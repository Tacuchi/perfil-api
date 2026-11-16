package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import net.tacuchi.perfil.soporte.BaseDeDatosDePrueba;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** La migración de las conexiones sobre una base que ya tiene datos de los esquemas anteriores. */
class MigracionConexionesTest {

    private static final List<String> TABLAS_ANTERIORES = List.of(
            "SELECT * FROM oferta ORDER BY id",
            "SELECT * FROM fuente ORDER BY nombre",
            "SELECT * FROM aviso ORDER BY dia",
            "SELECT * FROM lectura_dia ORDER BY fuente, dia");

    @Test
    void agregaLasConexionesVaciasSinCambiarNingunaFilaAnterior() throws SQLException {
        String esquema = "conexiones_" + System.nanoTime();
        flyway(esquema).target("2").load().migrate();
        try (Connection conexion = conectar(esquema); Statement sql = conexion.createStatement()) {
            sql.execute("""
                    INSERT INTO oferta (fuente, id_externo, titulo, modalidad, url, descripcion, encaje, motivos,
                                        tecnologias, clave_similitud, vista_primera, vista_ultima)
                    VALUES ('REMOTIVE', 'r-1', 'Backend Java', 'REMOTO', 'https://remotive.test/r-1', 'Spring',
                            'ALTO', ARRAY['java'], ARRAY['Java'], 'backend java', '2026-11-14T12:00:00Z',
                            '2026-11-15T12:00:00Z')""");
            sql.execute("""
                    INSERT INTO fuente (nombre, estado, detalle, ultima_lectura, ultima_correcta, ofertas_leidas) VALUES
                    ('REMOTIVE', 'CORRECTA', NULL, '2026-11-15T12:00:00Z', '2026-11-15T12:00:00Z', 4),
                    ('GET_ON_BOARD', 'FALLO', 'el sitio respondió HTTP 422', '2026-11-15T12:00:00Z', NULL, 0)""");
            sql.execute("INSERT INTO aviso (dia, enviado_en, ofertas) VALUES ('2026-11-14', '2026-11-15T03:31:00Z', 2)");
            sql.execute("INSERT INTO lectura_dia (fuente, dia, lecturas) VALUES ('REMOTE_OK', '2026-11-15', 3)");
        }
        List<List<String>> antes = filas(esquema);

        flyway(esquema).target("3").load().migrate();

        assertThat(filas(esquema)).isEqualTo(antes);
        try (Connection conexion = conectar(esquema); Statement sql = conexion.createStatement()) {
            assertThat(contar(sql, "conexion")).isZero();
            assertThat(contar(sql, "consumo_mes")).isZero();
        }
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(String esquema) {
        return Flyway.configure()
                .dataSource(BaseDeDatosDePrueba.url(), BaseDeDatosDePrueba.usuario(), BaseDeDatosDePrueba.clave())
                .schemas(esquema);
    }

    private static List<List<String>> filas(String esquema) throws SQLException {
        List<List<String>> todas = new ArrayList<>();
        try (Connection conexion = conectar(esquema); Statement sql = conexion.createStatement()) {
            for (String consulta : TABLAS_ANTERIORES) {
                List<String> deLaTabla = new ArrayList<>();
                try (ResultSet rs = sql.executeQuery(consulta)) {
                    int columnas = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        StringBuilder fila = new StringBuilder();
                        for (int i = 1; i <= columnas; i++) {
                            fila.append(rs.getMetaData().getColumnName(i)).append('=').append(rs.getString(i))
                                    .append(';');
                        }
                        deLaTabla.add(fila.toString());
                    }
                }
                assertThat(deLaTabla).as(consulta).isNotEmpty();
                todas.add(deLaTabla);
            }
        }
        return todas;
    }

    private static int contar(Statement sql, String tabla) throws SQLException {
        try (ResultSet rs = sql.executeQuery("SELECT count(*) FROM " + tabla)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Connection conectar(String esquema) throws SQLException {
        Connection conexion = DriverManager.getConnection(BaseDeDatosDePrueba.url(), BaseDeDatosDePrueba.usuario(),
                BaseDeDatosDePrueba.clave());
        try (Statement sql = conexion.createStatement()) {
            sql.execute("SET search_path TO " + esquema);
        }
        return conexion;
    }
}
