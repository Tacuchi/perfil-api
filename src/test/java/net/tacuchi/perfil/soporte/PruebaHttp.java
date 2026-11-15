package net.tacuchi.perfil.soporte;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import net.tacuchi.perfil.model.Fuente;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Base de las pruebas por HTTP: la API real en un puerto, Access y sitio de prueba, almacén en memoria. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({AlmacenDePruebaConfig.class, RelojDePruebaConfig.class})
public abstract class PruebaHttp {

    public static final String CABECERA_ACCESS = "Cf-Access-Jwt-Assertion";
    public static final String ORIGEN_PANEL = "https://panel.ejemplo.test";

    protected static final AccessDePrueba ACCESS = new AccessDePrueba();
    protected static final SitioDePrueba SITIO = new SitioDePrueba();
    protected static final FuentesDePrueba FUENTES = new FuentesDePrueba();

    @Autowired
    protected RestTestClient cliente;

    @Autowired
    protected AlmacenEnMemoria almacen;

    @Autowired
    protected RelojDePrueba reloj;

    @Autowired
    protected JdbcClient jdbc;

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
        registro.add("spring.datasource.url", BaseDeDatosDePrueba::url);
        registro.add("spring.datasource.username", BaseDeDatosDePrueba::usuario);
        registro.add("spring.datasource.password", BaseDeDatosDePrueba::clave);
        registro.add("perfil.ofertas.getonbrd-url", () -> FUENTES.url(Fuente.GET_ON_BOARD));
        registro.add("perfil.ofertas.remotive-url", () -> FUENTES.url(Fuente.REMOTIVE));
        registro.add("perfil.ofertas.remoteok-url", () -> FUENTES.url(Fuente.REMOTE_OK));
        // Las pruebas disparan la recolección a mano; el aviso se enciende solo donde se prueba.
        registro.add("perfil.ofertas.cron", () -> "${prueba.cron:-}");
        registro.add("perfil.aviso.activo", () -> "${prueba.aviso-activo:false}");
    }

    @BeforeEach
    void vaciarAlmacen() {
        almacen.vaciar();
    }

    @BeforeEach
    void vaciarOfertas() {
        jdbc.sql("TRUNCATE oferta, fuente, aviso, lectura_dia").update();
        reloj.reiniciar();
        FUENTES.reiniciar();
        FUENTES.usarReloj(reloj);
    }

    protected RestTestClient.ResponseSpec patchIdentificado(String ruta, String json) {
        return cliente.patch().uri(ruta)
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", ORIGEN_PANEL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
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

    protected RestTestClient.ResponseSpec postIdentificado(String ruta) {
        return cliente.post().uri(ruta)
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", ORIGEN_PANEL)
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
}
