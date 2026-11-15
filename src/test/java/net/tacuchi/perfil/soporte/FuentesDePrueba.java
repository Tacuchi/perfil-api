package net.tacuchi.perfil.soporte;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Reglas;

/**
 * Los tres sitios en local, con sus respuestas reales guardadas. Cada fuente puede responder bien, fallar
 * o bloquear, o entregar otro JSON; se cuenta cada petición (en total y por día de Lima del reloj que se le
 * indique) y se anota cada ruta pedida. Una fuente en pausa recibe la petición, la cuenta y retiene la respuesta
 * hasta que se la suelta: la pausa no cambia nada de lo que el sitio responde.
 */
public final class FuentesDePrueba {

    public enum Modo { BIEN, FALLA, BLOQUEA }

    static final Duration ESPERA_MAXIMA_PAUSA = Duration.ofSeconds(50);

    static final String PAGINA_VERIFICACION = "<!doctype html><title>Just a moment...</title>"
            + "<div id=\"challenge-platform\">captcha</div>";

    private final HttpServer servidor;
    private final Map<Fuente, Modo> modos = new ConcurrentHashMap<>();
    private final Map<Fuente, AtomicInteger> lecturas = new ConcurrentHashMap<>();
    private final Map<String, String> reemplazos = new ConcurrentHashMap<>();
    private final List<String> rutas = new CopyOnWriteArrayList<>();
    private final Map<Fuente, Map<LocalDate, AtomicInteger>> porDia = new ConcurrentHashMap<>();
    private final Map<Fuente, CountDownLatch> pausas = new ConcurrentHashMap<>();
    private volatile Clock reloj = Clock.systemUTC();

    public FuentesDePrueba() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Un hilo por petición: una fuente en pausa no retiene a las demás.
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
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

    /** Peticiones que recibió la fuente en ese día de Lima, según el reloj indicado con {@link #usarReloj}. */
    public int lecturas(Fuente fuente, LocalDate dia) {
        AtomicInteger cuenta = porDia.get(fuente).get(dia);
        return cuenta == null ? 0 : cuenta.get();
    }

    /** El reloj con el que se cuentan las peticiones por día: el mismo que usa la API. */
    public void usarReloj(Clock relojDeLaApi) {
        reloj = relojDeLaApi;
    }

    /** Desde ahora la fuente recibe y cuenta cada petición, pero no responde hasta {@link #soltar}. */
    public void pausar(Fuente fuente) {
        pausas.putIfAbsent(fuente, new CountDownLatch(1));
    }

    public void soltar(Fuente fuente) {
        CountDownLatch pausa = pausas.remove(fuente);
        if (pausa != null) {
            pausa.countDown();
        }
    }

    public boolean enPausa(Fuente fuente) {
        return pausas.containsKey(fuente);
    }

    public List<String> rutas() {
        return List.copyOf(rutas);
    }

    public void reiniciar() {
        for (Fuente fuente : Fuente.values()) {
            soltar(fuente);
            modos.put(fuente, Modo.BIEN);
            lecturas.put(fuente, new AtomicInteger());
            porDia.put(fuente, new ConcurrentHashMap<>());
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

    /** Anota y cuenta la petición como la recibió el sitio y, si la fuente está en pausa, espera a que se suelte. */
    private void recibir(HttpExchange intercambio, Fuente fuente) {
        rutas.add(intercambio.getRequestURI().toString());
        lecturas.get(fuente).incrementAndGet();
        LocalDate dia = LocalDate.ofInstant(reloj.instant(), Reglas.ZONA_PERSONA);
        porDia.get(fuente).computeIfAbsent(dia, d -> new AtomicInteger()).incrementAndGet();
        CountDownLatch pausa = pausas.get(fuente);
        if (pausa == null) {
            return;
        }
        try {
            // Por debajo de la espera de respuesta de la API (60 s): una pausa olvidada no se vuelve un fallo.
            pausa.await(ESPERA_MAXIMA_PAUSA.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
            recibir(intercambio, Fuente.GET_ON_BOARD);
            AccessDePrueba.escribir(intercambio, 422, "application/json",
                    "{\"message\":\"expand no es una lista\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        responder(intercambio, Fuente.GET_ON_BOARD, archivoGetOnBoard(intercambio));
    }

    private void responder(HttpExchange intercambio, Fuente fuente, String archivo) {
        recibir(intercambio, fuente);
        switch (modos.get(fuente)) {
            case FALLA -> AccessDePrueba.escribir(intercambio, 500, "text/plain", "error".getBytes(StandardCharsets.UTF_8));
            case BLOQUEA -> AccessDePrueba.escribir(intercambio, 403, "text/html",
                    PAGINA_VERIFICACION.getBytes(StandardCharsets.UTF_8));
            case BIEN -> AccessDePrueba.escribir(intercambio, 200, "application/json",
                    reemplazos.getOrDefault(archivo, guardada(archivo)).getBytes(StandardCharsets.UTF_8));
        }
    }
}
