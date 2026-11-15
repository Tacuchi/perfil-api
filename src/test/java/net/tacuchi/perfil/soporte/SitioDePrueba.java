package net.tacuchi.perfil.soporte;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** El sitio público en local: responde /404.html con estado 404 y su página, como el nginx del sitio. */
public final class SitioDePrueba {

    public static final String PAGINA_404 = "<!doctype html><title>404 del sitio</title><h1>Página del sitio</h1>";

    private final HttpServer servidor;

    public SitioDePrueba() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.createContext("/404.html", intercambio -> AccessDePrueba.escribir(intercambio, 404,
                "text/html; charset=utf-8", PAGINA_404.getBytes(StandardCharsets.UTF_8)));
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }
}
