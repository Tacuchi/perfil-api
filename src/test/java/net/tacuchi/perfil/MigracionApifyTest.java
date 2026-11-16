package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.tacuchi.perfil.config.PerfilProperties;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.service.CifradoClaves;
import net.tacuchi.perfil.soporte.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigracionApifyTest {
    @Test void actualizarDesdeConexiones_conservaClavePausasTopesConsumoYOfertas() throws Exception {
        String esquema = "apify_" + System.nanoTime();
        var flyway = Flyway.configure().dataSource(BaseDeDatosDePrueba.url(), "postgres", "postgres").schemas(esquema);
        flyway.target("3").load().migrate();
        byte[] llave = Base64.getDecoder().decode(PruebaHttp.LLAVE_DE_PRUEBA);
        byte[] vector = new byte[12];
        Arrays.fill(vector, (byte) 7);
        Cipher anterior = Cipher.getInstance("AES/GCM/NoPadding");
        anterior.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(llave, "AES"), new GCMParameterSpec(128, vector));
        anterior.updateAAD("COMPUTRABAJO".getBytes(StandardCharsets.UTF_8));
        byte[] texto = anterior.doFinal(ApifyDePrueba.CLAVE.getBytes(StandardCharsets.UTF_8));
        byte[] cifrada = ByteBuffer.allocate(vector.length + texto.length).put(vector).put(texto).array();
        try (Connection conexion = conectar(esquema)) {
            try (var sql = conexion.prepareStatement("INSERT INTO conexion VALUES ('COMPUTRABAJO', true, 'pausa', 5, ?, '2026-11-15T12:00Z')")) {
                sql.setBytes(1, cifrada); sql.executeUpdate();
            }
            try (Statement sql = conexion.createStatement()) {
                sql.execute("INSERT INTO conexion(fuente, pausada, tope_mensual) VALUES ('LINKEDIN', true, 2), ('BUMERAN', true, NULL)");
                sql.execute("INSERT INTO consumo_mes VALUES ('COMPUTRABAJO', '2026-11-01', 0.076)");
                sql.execute("""
                        INSERT INTO oferta (fuente, id_externo, titulo, modalidad, url, descripcion, encaje, motivos,
                                            tecnologias, clave_similitud, vista_primera, vista_ultima, estado, postulada_en)
                        VALUES ('LINKEDIN', '4487010792', 'Java', 'REMOTO', 'https://www.linkedin.com/jobs/view/4487010792/',
                            'Java', 'ALTO', ARRAY['java'], ARRAY['Java'], 'java', '2026-11-14T12:00Z',
                            '2026-11-15T12:00Z', 'POSTULADA', '2026-11-15T12:00Z')""");
            }
        }
        List<String> ofertas = filas(esquema, "SELECT row_to_json(o)::text FROM oferta o");
        List<String> consumo = filas(esquema, "SELECT row_to_json(c)::text FROM consumo_mes c");
        Flyway.configure().dataSource(BaseDeDatosDePrueba.url(), "postgres", "postgres").schemas(esquema).load().migrate();
        assertThat(filas(esquema, "SELECT row_to_json(o)::text FROM oferta o")).isEqualTo(ofertas);
        assertThat(filas(esquema, "SELECT row_to_json(c)::text FROM consumo_mes c")).isEqualTo(consumo);
        assertThat(filas(esquema, "SELECT fuente || ':' || tope_mensual || ':' || pausada FROM conexion ORDER BY fuente"))
                .containsExactly("BUMERAN:1.00:true", "COMPUTRABAJO:5.00:true", "LINKEDIN:2.00:true");
        try (Connection conexion = conectar(esquema); Statement sql = conexion.createStatement();
                ResultSet rs = sql.executeQuery("SELECT clave_cifrada, clave_cargada_en FROM conexion WHERE fuente='COMPUTRABAJO'")) {
            rs.next(); assertThat(rs.getBytes(1)).isEqualTo(cifrada);
            assertThat(rs.getTimestamp(2).toInstant()).isEqualTo(java.time.Instant.parse("2026-11-15T12:00:00Z"));
            var actual = new CifradoClaves(new PerfilProperties(null,null,null,null,null,null,null,null,
                    new PerfilProperties.Claves(PruebaHttp.LLAVE_DE_PRUEBA),null));
            assertThat(actual.descifrar(Fuente.COMPUTRABAJO, rs.getBytes(1)).equals(ApifyDePrueba.CLAVE)).isTrue();
        }
    }

    private static List<String> filas(String esquema, String consulta) throws SQLException {
        List<String> filas = new ArrayList<>();
        try (Connection conexion = conectar(esquema); Statement sql = conexion.createStatement(); ResultSet rs = sql.executeQuery(consulta)) {
            while (rs.next()) { filas.add(rs.getString(1)); }
        }
        return filas;
    }
    private static Connection conectar(String esquema) throws SQLException {
        Connection conexion = DriverManager.getConnection(BaseDeDatosDePrueba.url(), "postgres", "postgres");
        conexion.setSchema(esquema);
        return conexion;
    }
}
