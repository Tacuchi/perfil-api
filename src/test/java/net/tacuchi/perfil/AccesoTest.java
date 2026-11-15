package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;
import java.util.stream.Stream;
import net.tacuchi.perfil.soporte.Archivos;
import net.tacuchi.perfil.soporte.PruebaHttp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Quien no es la persona no entra a ninguna ruta privada. */
class AccesoTest extends PruebaHttp {

    private static final String ID = "00000000-0000-0000-0000-000000000000";

    static Stream<Arguments> rutasPrivadas() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/versiones"),
                Arguments.of(HttpMethod.POST, "/api/versiones"),
                Arguments.of(HttpMethod.GET, "/api/versiones/" + ID + "/pdf"),
                Arguments.of(HttpMethod.GET, "/api/versiones/" + ID + "/word"),
                Arguments.of(HttpMethod.PUT, "/api/vigente"),
                Arguments.of(HttpMethod.GET, "/cualquier/otra"),
                Arguments.of(HttpMethod.GET, "/cv/extra"),
                Arguments.of(HttpMethod.POST, "/cv"));
    }

    static Stream<Arguments> codigosRechazados() {
        return Stream.of(
                Arguments.of("sin código", (Supplier<String>) () -> null),
                Arguments.of("vencido", (Supplier<String>) ACCESS::codigoVencido),
                Arguments.of("de otra aplicación", (Supplier<String>) ACCESS::codigoDeOtraAplicacion),
                Arguments.of("de otro correo", (Supplier<String>) ACCESS::codigoDeOtroCorreo),
                Arguments.of("de otro emisor", (Supplier<String>) ACCESS::codigoDeOtroEmisor),
                Arguments.of("con firma ajena", (Supplier<String>) ACCESS::codigoConFirmaAjena),
                Arguments.of("ilegible", (Supplier<String>) () -> "no-es-un-jwt"));
    }

    static Stream<Arguments> combinaciones() {
        return rutasPrivadas().flatMap(ruta -> codigosRechazados()
                .map(codigo -> Arguments.of(ruta.get()[0], ruta.get()[1], codigo.get()[0], codigo.get()[1])));
    }

    @ParameterizedTest(name = "{0} {1} · {2}")
    @MethodSource("combinaciones")
    void cadaRutaPrivadaRechazaCadaCodigoInvalido(HttpMethod metodo, String ruta, String caso, Supplier<String> codigo) {
        RestTestClient.RequestBodySpec peticion = cliente.method(metodo).uri(ruta).header("Origin", ORIGEN_PANEL);
        String valor = codigo.get();
        if (valor != null) {
            peticion.header(CABECERA_ACCESS, valor);
        }
        peticion.exchange().expectStatus().isUnauthorized();
        assertThat(almacen.claves()).isEmpty();
    }

    /** Variantes codificadas o con parámetros de matriz de una ruta privada: nunca se sirven sin código. */
    @ParameterizedTest
    @ValueSource(strings = {"/api/versiones;x=1", "/api/%76ersiones", "/api//versiones", "/api/versiones/"})
    void unaVarianteDeRutaPrivadaTampocoEntra(String ruta) {
        cliente.get().uri(ruta).exchange().expectStatus().is4xxClientError();
    }

    @Test
    void conCodigoValidoLaPersonaEntra() {
        getIdentificado("/api/versiones").expectStatus().isOk().expectBody().json("[]");
    }

    @Test
    void unCambioQueNoVieneDelPanelSeRechazaAunqueTraigaCodigoValido() {
        cliente.put().uri("/api/vigente")
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", "https://otro-sitio.test")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"version\":\"" + ID + "\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);

        MultipartBodyBuilder cuerpo = new MultipartBodyBuilder();
        cuerpo.part("pdf", Archivos.pdf("x"));
        cliente.post().uri("/api/versiones")
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(cuerpo.build())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(almacen.claves()).isEmpty();
    }
}
