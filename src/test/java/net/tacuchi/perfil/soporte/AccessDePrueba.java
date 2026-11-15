package net.tacuchi.perfil.soporte;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Hace de equipo de Cloudflare Access en local: publica sus claves en la misma ruta que Cloudflare
 * y firma códigos con ellas. La API los verifica por el mismo camino que en producción.
 */
public final class AccessDePrueba {

    public static final String AUD = "aud-de-prueba";
    public static final String CORREO = "persona@ejemplo.test";

    private final RSAKey clave;
    private final RSAKey claveAjena;
    private final HttpServer servidor;

    public AccessDePrueba() {
        try {
            clave = new RSAKeyGenerator(2048).keyID("clave-prueba").generate();
            claveAjena = new RSAKeyGenerator(2048).keyID("clave-prueba").generate();
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (Exception e) {
            throw new IllegalStateException("no se pudo preparar Access de prueba", e);
        }
        byte[] claves = new JWKSet(clave.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        servidor.createContext("/cdn-cgi/access/certs", intercambio -> {
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, claves.length);
            intercambio.getResponseBody().write(claves);
            intercambio.close();
        });
        servidor.start();
    }

    public String emisor() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public String codigoValido() {
        return firmar(clave, emisor(), AUD, CORREO, Instant.now().plus(Duration.ofHours(1)));
    }

    public String codigoVencido() {
        return firmar(clave, emisor(), AUD, CORREO, Instant.now().minus(Duration.ofHours(1)));
    }

    public String codigoDeOtraAplicacion() {
        return firmar(clave, emisor(), "otra-aplicacion", CORREO, Instant.now().plus(Duration.ofHours(1)));
    }

    public String codigoDeOtroCorreo() {
        return firmar(clave, emisor(), AUD, "otra@ejemplo.test", Instant.now().plus(Duration.ofHours(1)));
    }

    public String codigoDeOtroEmisor() {
        return firmar(clave, "https://otro.cloudflareaccess.com", AUD, CORREO, Instant.now().plus(Duration.ofHours(1)));
    }

    public String codigoConFirmaAjena() {
        return firmar(claveAjena, emisor(), AUD, CORREO, Instant.now().plus(Duration.ofHours(1)));
    }

    public void detener() {
        servidor.stop(0);
    }

    private static String firmar(RSAKey firma, String emisor, String aud, String correo, Instant vence) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(firma.getKeyID()).type(JOSEObjectType.JWT).build(),
                    new JWTClaimsSet.Builder()
                            .issuer(emisor)
                            .audience(List.of(aud))
                            .claim("email", correo)
                            .issueTime(Date.from(vence.minus(Duration.ofHours(2))))
                            .notBeforeTime(Date.from(vence.minus(Duration.ofHours(2))))
                            .expirationTime(Date.from(vence))
                            .build());
            jwt.sign(new RSASSASigner(firma));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("no se pudo firmar el código de prueba", e);
        }
    }

    static void escribir(com.sun.net.httpserver.HttpExchange intercambio, int estado, String tipo, byte[] cuerpo) {
        try (intercambio) {
            intercambio.getResponseHeaders().add("Content-Type", tipo);
            intercambio.sendResponseHeaders(estado, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
