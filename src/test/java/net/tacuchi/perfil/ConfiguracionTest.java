package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** El servicio no arranca con la configuración incompleta y dice qué falta. */
class ConfiguracionTest {

    private static final Map<String, Object> COMPLETA = Map.of(
            "perfil.access.emisor", "https://equipo.cloudflareaccess.com",
            "perfil.access.aud", "aud",
            "perfil.access.correo", "persona@ejemplo.test",
            "perfil.almacen.endpoint", "https://cuenta.r2.cloudflarestorage.com",
            "perfil.almacen.bucket", "cv",
            "perfil.almacen.acceso", "id",
            "perfil.almacen.secreto", "secreto",
            "perfil.cv.archivo", "Nombre-CV.pdf",
            "perfil.sitio.url", "https://sitio.test",
            "perfil.panel.origen", "https://panel.test");

    @ParameterizedTest
    @ValueSource(strings = {"perfil.access.emisor", "perfil.access.aud", "perfil.access.correo",
            "perfil.almacen.endpoint", "perfil.almacen.bucket", "perfil.almacen.acceso", "perfil.almacen.secreto",
            "perfil.cv.archivo", "perfil.sitio.url", "perfil.panel.origen"})
    void sinUnValorObligatorioElArranqueFallaNombrandolo(String faltante) {
        Map<String, Object> propiedades = new HashMap<>(COMPLETA);
        propiedades.remove(faltante);
        String campo = faltante.substring("perfil.".length());

        assertThatThrownBy(() -> new SpringApplicationBuilder(PerfilApiApplication.class)
                .web(WebApplicationType.NONE)
                .properties(propiedades)
                .run().close())
                .rootCause()
                .hasMessageContaining("on field '" + campo + "'");
    }
}
