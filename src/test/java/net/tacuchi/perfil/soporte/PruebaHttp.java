package net.tacuchi.perfil.soporte;

import net.tacuchi.perfil.almacen.AlmacenArchivos;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Base de las pruebas por HTTP: la API real en un puerto, Access y sitio de prueba, almacén en memoria. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PruebaHttp.AlmacenDePrueba.class)
public abstract class PruebaHttp {

    public static final String CABECERA_ACCESS = "Cf-Access-Jwt-Assertion";
    public static final String ORIGEN_PANEL = "https://panel.ejemplo.test";

    protected static final AccessDePrueba ACCESS = new AccessDePrueba();
    protected static final SitioDePrueba SITIO = new SitioDePrueba();

    @Autowired
    protected RestTestClient cliente;

    @Autowired
    protected AlmacenEnMemoria almacen;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("perfil.access.emisor", ACCESS::emisor);
        registro.add("perfil.access.aud", () -> AccessDePrueba.AUD);
        registro.add("perfil.access.correo", () -> AccessDePrueba.CORREO);
        // Una subclase apunta a otro sitio con @TestPropertySource(properties = "prueba.sitio-url=...").
        registro.add("perfil.sitio.url", () -> "${prueba.sitio-url:" + SITIO.url() + "}");
        registro.add("perfil.panel.origen", () -> ORIGEN_PANEL);
        registro.add("perfil.cv.archivo", () -> "Nombre-Apellido-CV.pdf");
        registro.add("perfil.almacen.endpoint", () -> "http://127.0.0.1:1");
        registro.add("perfil.almacen.bucket", () -> "sin-uso");
        registro.add("perfil.almacen.acceso", () -> "sin-uso");
        registro.add("perfil.almacen.secreto", () -> "sin-uso");
    }

    @BeforeEach
    void vaciarAlmacen() {
        almacen.vaciar();
    }

    protected RestTestClient.ResponseSpec subir(byte[] pdf, byte[] word) {
        MultipartBodyBuilder cuerpo = new MultipartBodyBuilder();
        if (pdf != null) {
            cuerpo.part("pdf", recurso(pdf, "cv.pdf")).contentType(MediaType.APPLICATION_PDF);
        }
        if (word != null) {
            cuerpo.part("word", recurso(word, "cv.docx"));
        }
        return cliente.post().uri("/api/versiones")
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", ORIGEN_PANEL)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(cuerpo.build())
                .exchange();
    }

    protected String subirYObtenerId(byte[] pdf, byte[] word) {
        return subir(pdf, word).expectStatus().isCreated()
                .expectBody(VersionLeida.class).returnResult().getResponseBody().id();
    }

    protected RestTestClient.ResponseSpec dejarVigente(String id) {
        return cliente.put().uri("/api/vigente")
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", ORIGEN_PANEL)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"version\":\"" + id + "\"}")
                .exchange();
    }

    protected RestTestClient.ResponseSpec getIdentificado(String ruta) {
        return cliente.get().uri(ruta).header(CABECERA_ACCESS, ACCESS.codigoValido()).exchange();
    }

    protected byte[] cvPublico() {
        return cliente.get().uri("/cv").exchange().expectStatus().isOk()
                .expectBody(byte[].class).returnResult().getResponseBody();
    }

    private static ByteArrayResource recurso(byte[] contenido, String nombre) {
        return new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return nombre;
            }
        };
    }

    public record VersionLeida(String id, String fecha, boolean conWord, boolean vigente) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AlmacenDePrueba {

        @Bean
        @Primary
        AlmacenEnMemoria almacenEnMemoria() {
            return new AlmacenEnMemoria();
        }
    }
}
