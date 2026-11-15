package net.tacuchi.perfil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.tacuchi.perfil.soporte.AccessDePrueba;
import net.tacuchi.perfil.soporte.AlmacenDePruebaConfig;
import net.tacuchi.perfil.soporte.SitioDePrueba;
import org.springframework.boot.SpringApplication;

/**
 * Arranque local para recorrer panel y API juntos: `./mvnw spring-boot:test-run`.
 * La identidad pasa por la misma verificación que en producción, con claves de prueba publicadas en local;
 * el código firmado queda en target/codigo-local.jwt para que el proxy del panel lo envíe.
 * Solo existe en el classpath de pruebas: ni la clase ni el almacén en memoria entran al jar.
 */
public final class PerfilApiLocal {

    static final Path ARCHIVO_CODIGO = Path.of("target", "codigo-local.jwt");
    static final String ORIGEN_PANEL_LOCAL = "http://localhost:4200";

    private PerfilApiLocal() {
    }

    public static void main(String[] args) throws IOException {
        AccessDePrueba access = new AccessDePrueba();
        SitioDePrueba sitio = new SitioDePrueba();
        Files.createDirectories(ARCHIVO_CODIGO.getParent());
        Files.writeString(ARCHIVO_CODIGO, access.codigoValido(Duration.ofHours(12)));

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
                "--perfil.almacen.secreto=sin-uso"));
        argumentos.addAll(List.of(args));
        SpringApplication.from(PerfilApiApplication::main)
                .with(AlmacenDePruebaConfig.class)
                .run(argumentos.toArray(String[]::new));
    }
}
