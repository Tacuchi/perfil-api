package net.tacuchi.perfil.soporte;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.tacuchi.perfil.model.Fuente;

/**
 * Los tres sitios en local, con sus respuestas reales guardadas. Cada fuente puede responder bien, fallar
 * o bloquear, o entregar otro JSON; se cuenta cada petición y se anota cada ruta pedida.
 */
public final class FuentesDePrueba {

    public enum Modo { BIEN, FALLA, BLOQUEA }

    static final String PAGINA_VERIFICACION = "<!doctype html><title>Just a moment...</title>"
            + "<div id=\"challenge-platform\">captcha</div>";

    private final HttpServer servidor;
    private final Map<Fuente, Modo> modos = new ConcurrentHashMap<>();
    private final Map<Fuente, AtomicInteger> lecturas = new ConcurrentHashMap<>();
    private final Map<String, String> reemplazos = new ConcurrentHashMap<>();
    private final List<String> rutas = new CopyOnWriteArrayList<>();

    public FuentesDePrueba() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.createContext("/getonbrd/", this::responderGetOnBoard);
        servidor.createContext("/remotive/", i -> responder(i, Fuente.REMOTIVE, "remotive.json"));
        servidor.createContext("/remoteok/", i -> responder(i, Fuente.REMOTE_OK, "remoteok.json"));
        servidor.start();
        reiniciar();
    }

    public String url(Fuente fuente) {
        String base = "http://127.0.0.1:" + servidor.getAddress().getPort();
        return switch (fuente) {
            case GET_ON_BOARD -> base + "/getonbrd";
            case REMOTIVE -> base + "/remotive";
            case REMOTE_OK -> base + "/remoteok";
        };
    }

    public void modo(Fuente fuente, Modo modo) {
        modos.put(fuente, modo);
    }

    /** Sirve este JSON en lugar del archivo guardado (por ejemplo, para una lectura sin cierta oferta). */
    public void reemplazar(String archivo, String json) {
        reemplazos.put(archivo, json);
    }

    public int lecturas(Fuente fuente) {
        return lecturas.get(fuente).get();
    }

    public List<String> rutas() {
        return List.copyOf(rutas);
    }

    public void reiniciar() {
        for (Fuente fuente : Fuente.values()) {
            modos.put(fuente, Modo.BIEN);
            lecturas.put(fuente, new AtomicInteger());
        }
        reemplazos.clear();
        rutas.clear();
    }

    public static String guardada(String archivo) {
        try (InputStream entrada = FuentesDePrueba.class.getResourceAsStream("/fuentes/" + archivo)) {
            if (entrada == null) {
                throw new IllegalStateException("no existe la respuesta guardada /fuentes/" + archivo);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Get on Board: una respuesta por categoría; las categorías sin respuesta guardada llegan vacías. */
    private static String archivoGetOnBoard(HttpExchange intercambio) {
        String ruta = intercambio.getRequestURI().getPath();
        if (ruta.contains("/categories/programming/")) {
            return "getonbrd-programming.json";
        }
        if (ruta.contains("/categories/mobile-developer/")) {
            return "getonbrd-mobile-developer.json";
        }
        return "getonbrd-vacia.json";
    }

    /** Como el sitio real: si «expand» no llega como una lista JSON, responde 422. */
    private void responderGetOnBoard(HttpExchange intercambio) {
        String consulta = intercambio.getRequestURI().getQuery();
        if (consulta == null || !consulta.contains("expand=[\"company\"]")) {
            rutas.add(intercambio.getRequestURI().toString());
            lecturas.get(Fuente.GET_ON_BOARD).incrementAndGet();
            AccessDePrueba.escribir(intercambio, 422, "application/json",
                    "{\"message\":\"expand no es una lista\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        responder(intercambio, Fuente.GET_ON_BOARD, archivoGetOnBoard(intercambio));
    }

    private void responder(HttpExchange intercambio, Fuente fuente, String archivo) {
        rutas.add(intercambio.getRequestURI().toString());
        lecturas.get(fuente).incrementAndGet();
        switch (modos.get(fuente)) {
            case FALLA -> AccessDePrueba.escribir(intercambio, 500, "text/plain", "error".getBytes(StandardCharsets.UTF_8));
            case BLOQUEA -> AccessDePrueba.escribir(intercambio, 403, "text/html",
                    PAGINA_VERIFICACION.getBytes(StandardCharsets.UTF_8));
            case BIEN -> AccessDePrueba.escribir(intercambio, 200, "application/json",
                    reemplazos.getOrDefault(archivo, guardada(archivo)).getBytes(StandardCharsets.UTF_8));
        }
    }
}
