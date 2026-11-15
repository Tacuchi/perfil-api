package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.tacuchi.perfil.soporte.Archivos;
import net.tacuchi.perfil.soporte.PruebaHttp;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;

/** Subir, listar, descargar, restaurar y el tope de 10. */
class HistorialTest extends PruebaHttp {

    private static final ParameterizedTypeReference<List<VersionLeida>> LISTA = new ParameterizedTypeReference<>() {
    };

    @Test
    void cadaCargaQuedaEnElHistorialConSuFechaYLaUltimaEsLaVigente() {
        String primera = subirYObtenerId(Archivos.pdf("uno"), null);
        String segunda = subirYObtenerId(Archivos.pdf("dos"), Archivos.docx("dos"));

        List<VersionLeida> historial = listar();
        assertThat(historial).extracting(VersionLeida::id).containsExactly(segunda, primera);
        assertThat(historial).allSatisfy(v -> assertThat(v.fecha()).isNotBlank());
        assertThat(historial.get(0)).extracting(VersionLeida::vigente, VersionLeida::conWord).containsExactly(true, true);
        assertThat(historial.get(1)).extracting(VersionLeida::vigente, VersionLeida::conWord).containsExactly(false, false);
    }

    @Test
    void laPersonaDescargaCualquierVersionYSuWord() {
        String primera = subirYObtenerId(Archivos.pdf("uno"), Archivos.docx("uno"));
        subirYObtenerId(Archivos.pdf("dos"), null);

        assertThat(getIdentificado("/api/versiones/" + primera + "/pdf").expectStatus().isOk()
                .expectHeader().valueMatches("Content-Disposition", "attachment;.*\\.pdf.*")
                .expectBody(byte[].class).returnResult().getResponseBody()).isEqualTo(Archivos.pdf("uno"));
        assertThat(getIdentificado("/api/versiones/" + primera + "/word").expectStatus().isOk()
                .expectHeader().valueMatches("Content-Disposition", "attachment;.*\\.docx.*")
                .expectBody(byte[].class).returnResult().getResponseBody()).isEqualTo(Archivos.docx("uno"));
    }

    @Test
    void unaVersionSinWordOInexistenteRespondeNoEncontrado() {
        String sinWord = subirYObtenerId(Archivos.pdf("uno"), null);
        getIdentificado("/api/versiones/" + sinWord + "/word").expectStatus().isNotFound();
        getIdentificado("/api/versiones/no-existe/pdf").expectStatus().isNotFound();
        dejarVigente("no-existe").expectStatus().isNotFound();
    }

    @Test
    void escenarioVolverAUnaVersionAnterior() {
        String primera = subirYObtenerId(Archivos.pdf("uno"), null);
        String segunda = subirYObtenerId(Archivos.pdf("dos"), null);

        dejarVigente(primera).expectStatus().isOk();

        assertThat(cvPublico()).isEqualTo(Archivos.pdf("uno"));
        assertThat(listar()).extracting(VersionLeida::id).containsExactlyInAnyOrder(primera, segunda);
        assertThat(listar()).filteredOn(VersionLeida::vigente).extracting(VersionLeida::id).containsExactly(primera);
    }

    @Test
    void alSubirLaUndecimaSeVaLaMasAntiguaConSuWord() {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ids.add(subirYObtenerId(Archivos.pdf("v" + i), Archivos.docx("v" + i)));
        }
        String undecima = subirYObtenerId(Archivos.pdf("v11"), null);

        assertThat(listar()).hasSize(10).extracting(VersionLeida::id).doesNotContain(ids.get(0)).contains(undecima);
        assertThat(almacen.claves()).noneMatch(clave -> clave.contains(ids.get(0)));
    }

    @Test
    void escenarioElHistorialLlenoNoExpulsaALaVigente() {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ids.add(subirYObtenerId(Archivos.pdf("v" + i), null));
        }
        dejarVigente(ids.get(0)).expectStatus().isOk();

        String nueva = subirYObtenerId(Archivos.pdf("nueva"), null);

        List<VersionLeida> historial = listar();
        assertThat(historial).hasSize(10);
        assertThat(historial).filteredOn(VersionLeida::vigente).extracting(VersionLeida::id).containsExactly(nueva);
        assertThat(historial).extracting(VersionLeida::id).contains(ids.get(0)).doesNotContain(ids.get(1));
    }

    @Test
    void unPdfQueNoEsPdfSeRechazaPorTipoSinGuardarNada() {
        subir("no soy un pdf".getBytes(), null).expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .expectBody()
                .jsonPath("$.campo").isEqualTo("pdf")
                .jsonPath("$.motivo").isEqualTo("TIPO")
                .jsonPath("$.detail").isEqualTo("El archivo principal no es un PDF.");
        assertThat(almacen.claves()).isEmpty();
    }

    @Test
    void unPdfDeMasDe10MBSeRechazaPorTamanoSinGuardarNada() {
        subir(Archivos.pdfGrande(), null).expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                .expectBody()
                .jsonPath("$.campo").isEqualTo("pdf")
                .jsonPath("$.motivo").isEqualTo("TAMANO")
                .jsonPath("$.detail").isEqualTo("El PDF supera el tamaño máximo de 10 MB.");
        assertThat(almacen.claves()).isEmpty();
    }

    @Test
    void unWordQueNoEsWordRechazaLaCargaEnteraSinGuardarNada() {
        subir(Archivos.pdf("uno"), Archivos.zipSinWord()).expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .expectBody()
                .jsonPath("$.campo").isEqualTo("word")
                .jsonPath("$.motivo").isEqualTo("TIPO")
                .jsonPath("$.detail").isEqualTo("El adjunto no es un documento de Word (.docx).");
        subir(Archivos.pdf("uno"), Archivos.pdf("disfrazado")).expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(almacen.claves()).isEmpty();
    }

    @Test
    void unWordDeMasDe10MBRechazaLaCargaEnteraSinGuardarNada() {
        subir(Archivos.pdf("uno"), Archivos.pdfGrande()).expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                .expectBody()
                .jsonPath("$.campo").isEqualTo("word")
                .jsonPath("$.motivo").isEqualTo("TAMANO");
        assertThat(almacen.claves()).isEmpty();
    }

    @Test
    void unaCargaSinPdfSeRechaza() {
        subir(null, Archivos.docx("solo")).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.campo").isEqualTo("pdf");
        assertThat(almacen.claves()).isEmpty();
    }

    private List<VersionLeida> listar() {
        return getIdentificado("/api/versiones").expectStatus().isOk()
                .expectBody(LISTA).returnResult().getResponseBody();
    }
}
