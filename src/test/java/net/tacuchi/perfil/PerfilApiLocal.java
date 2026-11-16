package net.tacuchi.perfil;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.tacuchi.perfil.local.ArranqueLocalConfig;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.soporte.AccessDePrueba;
import net.tacuchi.perfil.soporte.ApifyDePrueba;
import net.tacuchi.perfil.soporte.BaseDeDatosDePrueba;
import net.tacuchi.perfil.soporte.FuentesDePrueba;
import net.tacuchi.perfil.soporte.PruebaHttp;
import net.tacuchi.perfil.soporte.SitioDePrueba;
import org.springframework.boot.SpringApplication;

/**
 * Arranque local para recorrer panel y API juntos: `./mvnw spring-boot:test-run`.
 * La identidad pasa por la misma verificación que en producción, con claves de prueba publicadas en local;
 * el código firmado queda en target/codigo-local.jwt para que el proxy del panel lo envíe, y el del token de servicio
 * del lector en target/codigo-lector-local.jwt.
 * Las ofertas van a un PostgreSQL de pruebas, las tres fuentes se sirven desde sus respuestas guardadas, Computrabajo
 * se lee contra el doble de Apify (con la clave {@code clave-de-prueba}) y el
 * correo diario llega a un servidor de correo de prueba. Los mandos del recorrido viven en /api/local.
 * Solo existe en el classpath de pruebas: nada de esto entra al jar.
 */
public final class PerfilApiLocal {

    static final Path ARCHIVO_CODIGO = Path.of("target", "codigo-local.jwt");
    /** Código del token de servicio del lector, para correr el lector contra la API local. */
    static final Path ARCHIVO_CODIGO_LECTOR = Path.of("target", "codigo-lector-local.jwt");
    static final String ORIGEN_PANEL_LOCAL = "http://localhost:4200";

    private PerfilApiLocal() {
    }

    public static void main(String[] args) throws IOException {
        AccessDePrueba access = new AccessDePrueba();
        SitioDePrueba sitio = new SitioDePrueba();
        FuentesDePrueba fuentes = new FuentesDePrueba();
        ApifyDePrueba apify = new ApifyDePrueba();
        GreenMail correo = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
        correo.start();
        Files.createDirectories(ARCHIVO_CODIGO.getParent());
        Files.writeString(ARCHIVO_CODIGO, access.codigoValido(Duration.ofHours(12)));
        Files.writeString(ARCHIVO_CODIGO_LECTOR, access.codigoDelLector(Duration.ofHours(12)));

        List<String> argumentos = new ArrayList<>(List.of(
                "--perfil.access.emisor=" + access.emisor(),
                "--perfil.access.aud=" + AccessDePrueba.AUD,
                "--perfil.access.correo=" + AccessDePrueba.CORREO,
                "--perfil.sitio.url=" + sitio.url(),
                "--perfil.panel.origen=" + ORIGEN_PANEL_LOCAL,
                "--perfil.cv.archivo=CV-local.pdf",
                "--perfil.almacen.endpoint=http://127.0.0.1:1",
                "--perfil.almacen.bucket=sin-uso",
                "--perfil.almacen.acceso=sin-uso",
                "--perfil.almacen.secreto=sin-uso",
                "--spring.datasource.url=" + BaseDeDatosDePrueba.url(),
                "--spring.datasource.username=" + BaseDeDatosDePrueba.usuario(),
                "--spring.datasource.password=" + BaseDeDatosDePrueba.clave(),
                "--perfil.ofertas.getonbrd-url=" + fuentes.url(Fuente.GET_ON_BOARD),
                "--perfil.ofertas.remotive-url=" + fuentes.url(Fuente.REMOTIVE),
                "--perfil.ofertas.remoteok-url=" + fuentes.url(Fuente.REMOTE_OK),
                // La recolección se dispara con los mandos de /api/local, no por horario.
                "--perfil.ofertas.cron=-",
                "--perfil.lector.token-id=" + AccessDePrueba.TOKEN_LECTOR,
                // Computrabajo contra el doble de Apify: su clave es ApifyDePrueba.CLAVE; la llave, una inventada.
                "--perfil.computrabajo.url=" + apify.url(),
                "--perfil.claves.llave=" + PruebaHttp.LLAVE_DE_PRUEBA,
                "--perfil.aviso.activo=true",
                "--perfil.aviso.remitente=avisos@ejemplo.test",
                "--spring.mail.host=127.0.0.1",
                "--spring.mail.port=" + correo.getSmtp().getPort()));
        argumentos.addAll(List.of(args));
        ArranqueLocalConfig.preparar(fuentes, apify, correo);
        SpringApplication.from(PerfilApiApplication::main)
                .with(ArranqueLocalConfig.class)
                .run(argumentos.toArray(String[]::new));
    }
}
