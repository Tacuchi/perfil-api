package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import net.tacuchi.perfil.soporte.PruebaHttp;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Si el sitio no contesta, /cv sin vigente sigue respondiendo «no encontrado». */
// Puerto 1: nadie escucha, la conexión se rechaza al instante.
@TestPropertySource(properties = "prueba.sitio-url=http://127.0.0.1:1")
class CvPublicoSitioCaidoTest extends PruebaHttp {

    @Test
    void sinVigenteYSinSitioRespondeUnNoEncontradoSimple() {
        cliente.get().uri("/cv").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class).value(cuerpo -> assertThat(cuerpo)
                        .contains("No encontrado"));
    }
}
