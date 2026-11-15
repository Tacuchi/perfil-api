package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import net.tacuchi.perfil.soporte.Archivos;
import net.tacuchi.perfil.soporte.PruebaHttp;
import net.tacuchi.perfil.soporte.SitioDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** La dirección pública. */
class CvPublicoTest extends PruebaHttp {

    @Test
    void entregaElPdfVigenteParaVerloEnElNavegadorSinIdentificarse() {
        subirYObtenerId(Archivos.pdf("vigente"), Archivos.docx("vigente"));

        cliente.get().uri("/cv").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectHeader().valueMatches("Content-Disposition", "inline; filename=\"Nombre-Apellido-CV\\.pdf\".*")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().doesNotExist("X-Robots-Tag")
                .expectBody(byte[].class).isEqualTo(Archivos.pdf("vigente"));
    }

    @Test
    void escenarioUnaVersionNuevaReemplazaALaAnteriorDeInmediato() {
        String anterior = subirYObtenerId(Archivos.pdf("anterior"), null);
        assertThat(cvPublico()).isEqualTo(Archivos.pdf("anterior"));

        subirYObtenerId(Archivos.pdf("nueva"), null);

        assertThat(cvPublico()).isEqualTo(Archivos.pdf("nueva"));
        getIdentificado("/api/versiones/" + anterior + "/pdf").expectStatus().isOk();
        cliente.get().uri("/api/versiones/" + anterior + "/pdf").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void sinCvVigenteRespondeNoEncontradoConLaPaginaDelSitio() {
        cliente.get().uri("/cv").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectBody(String.class).isEqualTo(SitioDePrueba.PAGINA_404);
    }

    @Test
    void elWordNoSaleNuncaPorLaDireccionPublica() {
        subirYObtenerId(Archivos.pdf("con-word"), Archivos.docx("con-word"));
        assertThat(cvPublico()).isEqualTo(Archivos.pdf("con-word"));
        cliente.get().uri("/cv/word").exchange().expectStatus().isUnauthorized();
    }
}
