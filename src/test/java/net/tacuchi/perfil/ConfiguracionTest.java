package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import net.tacuchi.perfil.soporte.BaseDeDatosDePrueba;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** El servicio no arranca con la configuración incompleta y dice qué falta. */
class ConfiguracionTest {

    private static Map<String, Object> completa() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("perfil.access.emisor", "https://equipo.cloudflareaccess.com");
        propiedades.put("perfil.access.aud", "aud");
        propiedades.put("perfil.access.correo", "persona@ejemplo.test");
        propiedades.put("perfil.almacen.endpoint", "https://cuenta.r2.cloudflarestorage.com");
        propiedades.put("perfil.almacen.bucket", "cv");
        propiedades.put("perfil.almacen.acceso", "id");
        propiedades.put("perfil.almacen.secreto", "secreto");
        propiedades.put("perfil.cv.archivo", "Nombre-CV.pdf");
        propiedades.put("perfil.sitio.url", "https://sitio.test");
        propiedades.put("perfil.panel.origen", "https://panel.test");
        propiedades.put("perfil.aviso.activo", "false");
        propiedades.put("perfil.ofertas.cron", "-");
        propiedades.put("spring.datasource.url", BaseDeDatosDePrueba.url());
        propiedades.put("spring.datasource.username", BaseDeDatosDePrueba.usuario());
        propiedades.put("spring.datasource.password", BaseDeDatosDePrueba.clave());
        return propiedades;
    }

    @ParameterizedTest
    @ValueSource(strings = {"perfil.access.emisor", "perfil.access.aud", "perfil.access.correo",
            "perfil.almacen.endpoint", "perfil.almacen.bucket", "perfil.almacen.acceso", "perfil.almacen.secreto",
            "perfil.cv.archivo", "perfil.sitio.url", "perfil.panel.origen", "perfil.aviso.activo"})
    void sinUnValorObligatorioElArranqueFallaNombrandolo(String faltante) {
        Map<String, Object> propiedades = completa();
        propiedades.remove(faltante);
        String campo = faltante.substring("perfil.".length());

        assertThatThrownBy(() -> arrancar(propiedades))
                .rootCause()
                .hasMessageContaining("on field '" + campo + "'");
    }

    /**
     * Sin URL Spring Boot no encuentra el controlador de la base. Su informe de arranque fallido lo traduce como
     * «Failed to configure a DataSource: 'url' attribute is not specified», que es lo que lee quien despliega.
     */
    @Test
    void sinBaseDeDatosElArranqueFalla() {
        Map<String, Object> propiedades = completa();
        propiedades.remove("spring.datasource.url");

        assertThatThrownBy(() -> arrancar(propiedades))
                .rootCause()
                .hasMessageContaining("suitable driver class");
    }

    @Test
    void conElAvisoEncendidoSinRemitenteElArranqueLoNombra() {
        Map<String, Object> propiedades = completa();
        propiedades.put("perfil.aviso.activo", "true");
        propiedades.put("spring.mail.host", "127.0.0.1");

        assertThatThrownBy(() -> arrancar(propiedades))
                .rootCause()
                .hasMessageContaining("perfil.aviso.remitente");
    }

    @Test
    void conElAvisoEncendidoSinServidorDeCorreoElArranqueLoNombra() {
        Map<String, Object> propiedades = completa();
        propiedades.put("perfil.aviso.activo", "true");
        propiedades.put("perfil.aviso.remitente", "avisos@ejemplo.test");

        assertThatThrownBy(() -> arrancar(propiedades))
                .rootCause()
                .hasMessageContaining("spring.mail.host");
    }

    private static void arrancar(Map<String, Object> propiedades) {
        new SpringApplicationBuilder(PerfilApiApplication.class)
                .web(WebApplicationType.NONE)
                .properties(propiedades)
                .run()
                .close();
    }
}
