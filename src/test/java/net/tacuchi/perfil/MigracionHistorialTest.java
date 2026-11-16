package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.*;
import java.util.*;
import net.tacuchi.perfil.soporte.BaseDeDatosDePrueba;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigracionHistorialTest {
    @Test
    void migrar_desdeVersionAnterior_conservaTodasLasFilas() throws Exception {
        String esquema = "historial_" + System.nanoTime();
        var configuracion = Flyway.configure().dataSource(BaseDeDatosDePrueba.url(), "postgres", "postgres").schemas(esquema);
        configuracion.target("4").load().migrate();
        try (var conexion = conectar(esquema); var sql = conexion.createStatement()) {
            sql.execute("INSERT INTO conexion VALUES ('COMPUTRABAJO', true, 'pausa', 5, decode('010203','hex'), '2026-11-15T12:00Z')");
            sql.execute("INSERT INTO consumo_mes VALUES ('COMPUTRABAJO','2026-11-01',0.076)");
            sql.execute("INSERT INTO fuente VALUES ('REMOTIVE','CORRECTA',NULL,'2026-11-15T12:00Z','2026-11-15T12:00Z',1)");
            sql.execute("""
                    INSERT INTO oferta(fuente,id_externo,titulo,modalidad,url,descripcion,encaje,motivos,tecnologias,clave_similitud,vista_primera,vista_ultima)
                    VALUES ('REMOTIVE','123','Java','REMOTO','https://ejemplo.test','Java','ALTO',ARRAY['java'],ARRAY['Java'],'java','2026-11-15T12:00Z','2026-11-15T12:00Z')
                    """);
        }
        Map<String,List<String>> anteriores = new HashMap<>();
        for (String tabla : List.of("oferta","fuente","conexion","consumo_mes")) anteriores.put(tabla, filas(esquema, tabla));
        Flyway.configure().dataSource(BaseDeDatosDePrueba.url(), "postgres", "postgres").schemas(esquema).load().migrate();
        for (var entrada : anteriores.entrySet()) assertThat(filas(esquema, entrada.getKey())).isEqualTo(entrada.getValue());
        assertThat(filas(esquema,"lectura_historial")).isEmpty();
    }

    private static List<String> filas(String esquema, String tabla) throws SQLException {
        List<String> filas = new ArrayList<>();
        try (var conexion = conectar(esquema); var sql = conexion.createStatement();
                var rs = sql.executeQuery("SELECT row_to_json(t)::text FROM " + tabla + " t")) {
            while (rs.next()) filas.add(rs.getString(1));
        }
        return filas;
    }
    private static Connection conectar(String esquema) throws SQLException {
        var conexion = DriverManager.getConnection(BaseDeDatosDePrueba.url(), "postgres", "postgres");
        conexion.setSchema(esquema);
        return conexion;
    }
}
