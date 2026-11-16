package net.tacuchi.perfil.soporte;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * La API de Apify en local, tan estricta como la real en lo que la API usa: exige la clave en la cabecera de
 * autorización (401 sin ella o con otra), exige el gasto y el número de avisos máximos al lanzar, respeta los dos al
 * entregar, informa el costo de la corrida con los precios reales del actor y sirve los avisos guardados de la prueba
 * real. Como la real, recién terminada la corrida informa solo el costo del arranque; el resto se asienta después de
 * unas consultas, o nunca. Cuenta cada consulta.
 */
public final class ApifyDePrueba {

    /** Una clave inventada: ninguna clave real pasa por las pruebas. */
    public static final String CLAVE = "clave-de-prueba";
    public static final String LINKEDIN = "valig~linkedin-jobs-scraper";
    public static final String BUMERAN = "blackfalcondata~bumeran-scraper";
    public static final String ACTOR = "santamaria-automations~computrabajo-scraper";
    public static final BigDecimal PRECIO_ARRANQUE = new BigDecimal("0.001");
    public static final BigDecimal PRECIO_AVISO = new BigDecimal("0.003");
    private static final String EVENTO_ARRANQUE = "apify-actor-start";
    private static final String EVENTO_AVISO = "apify-default-dataset-item";

    /** TERMINA: la corrida acaba dentro de la espera del lanzamiento; TARDA: acaba en la primera consulta. */
    public enum Modo { TERMINA, TARDA, FALLA, VACIA, NO_TERMINA, HTTP401, HTTP429, JSON_INVALIDO, SIN_PRECIO, SIN_COSTO }

    /** Una petición tal como llegó: sin la clave, solo si traía la correcta. */
    public record Peticion(String metodo, String ruta, Map<String, String> parametros, boolean conClave,
            JsonNode cuerpo) {
    }

    private final HttpServer servidor;
    private final JsonMapper json = JsonMapper.builder().build();
    private final JsonNode avisos;
    private final JsonNode precios;
    private final List<Peticion> peticiones = new CopyOnWriteArrayList<>();
    private final Map<String, Corrida> corridas = new ConcurrentHashMap<>();
    private final Map<String, Modo> modos = new ConcurrentHashMap<>();
    private final Map<String, JsonNode> muestras = new HashMap<>();
    private final AtomicInteger secuencia = new AtomicInteger();
    private volatile Modo modo;
    private volatile int consultasParaAsentar;

    public ApifyDePrueba() {
        avisos = json.readTree(FuentesDePrueba.guardada("apify-computrabajo.json"));
        muestras.put(LINKEDIN, json.readTree(FuentesDePrueba.guardada("apify-linkedin.json")));
        muestras.put(BUMERAN, json.readTree(FuentesDePrueba.guardada("apify-bumeran.json")));
        precios = json.readTree(FuentesDePrueba.guardada("apify-corrida.json")).path("pricingInfo");
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.createContext("/v2/", this::atender);
        servidor.start();
        reiniciar();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public void reiniciar() {
        peticiones.clear();
        modos.clear();
        corridas.clear();
        modo = Modo.TERMINA;
        consultasParaAsentar = 1;
    }

    public void modo(String actor, String ambito, String termino, Modo nuevo) {
        modos.put(actor + "|" + ambito + "|" + termino, nuevo);
    }

    public long lanzamientos(String actor) {
        return peticiones.stream().filter(p -> p.metodo().equals("POST")
                && p.ruta().equals("/v2/acts/" + actor + "/runs")).count();
    }

    public void modo(Modo nuevo) {
        modo = nuevo;
    }

    /** Consultas de la corrida tras las que su costo queda asentado; 0: ya al lanzarla; negativo: nunca. */
    public void asentarTras(int consultas) {
        consultasParaAsentar = consultas;
    }

    public int consultas() {
        return peticiones.size();
    }

    public List<Peticion> peticiones() {
        return List.copyOf(peticiones);
    }

    public int avisosGuardados() {
        return avisos.size();
    }

    /** Lo que cobra una corrida que entrega esos avisos, con los precios del actor. */
    public static BigDecimal costo(int avisos) {
        return PRECIO_ARRANQUE.add(PRECIO_AVISO.multiply(BigDecimal.valueOf(avisos)));
    }

    private void atender(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        Map<String, String> parametros = parametros(intercambio.getRequestURI().getRawQuery());
        String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
        boolean conClave = ("Bearer " + CLAVE).equals(autorizacion);
        byte[] crudo = intercambio.getRequestBody().readAllBytes();
        JsonNode cuerpo = crudo.length == 0 ? null : json.readTree(crudo);
        peticiones.add(new Peticion(intercambio.getRequestMethod(), ruta, parametros, conClave, cuerpo));
        if (autorizacion == null) {
            error(intercambio, 401, "token-not-provided", "Authentication token was not provided");
            return;
        }
        if (!conClave) {
            error(intercambio, 401, "user-or-token-not-found", "User was not found or authentication token is not valid");
            return;
        }
        if ("POST".equals(intercambio.getRequestMethod()) && List.of(ACTOR, LINKEDIN, BUMERAN).stream().anyMatch(a -> ruta.equals("/v2/acts/" + a + "/runs"))) {
            lanzar(intercambio, parametros, cuerpo, ruta.substring("/v2/acts/".length(), ruta.length() - "/runs".length()));
        } else if ("GET".equals(intercambio.getRequestMethod()) && ruta.startsWith("/v2/actor-runs/")) {
            consultar(intercambio, ruta.substring("/v2/actor-runs/".length()));
        } else if ("GET".equals(intercambio.getRequestMethod()) && ruta.startsWith("/v2/datasets/")
                && ruta.endsWith("/items")) {
            servirAvisos(intercambio, ruta.substring("/v2/datasets/".length(), ruta.length() - "/items".length()));
        } else {
            error(intercambio, 404, "page-not-found", "We have bad news: there is nothing here");
        }
    }

    private void lanzar(HttpExchange intercambio, Map<String, String> parametros, JsonNode entrada, String actor) throws IOException {
        BigDecimal gastoMaximo;
        int maxAvisos;
        try {
            gastoMaximo = new BigDecimal(parametros.get("maxTotalChargeUsd"));
            maxAvisos = Integer.parseInt(parametros.get("maxItems"));
        } catch (RuntimeException e) {
            error(intercambio, 400, "invalid-input", "maxTotalChargeUsd y maxItems son obligatorios en esta prueba");
            return;
        }
        if (!entradaValida(actor, entrada, maxAvisos) || parametros.containsKey("token")
                || intercambio.getRequestHeaders().containsKey("Cookie") || gastoMaximo.signum() <= 0) {
            error(intercambio, 400, "invalid-input", "Input is not valid");
            return;
        }
        String ambito = actor.equals(LINKEDIN) ? entrada.path("location").asString("") : "PE";
        String termino = entrada.path(actor.equals(LINKEDIN) ? "keywords" : "query").asString("");
        Modo actual = modos.getOrDefault(actor + "|" + ambito + "|" + termino,
                modos.getOrDefault(actor + "|" + ambito + "|*", modo));
        if (actual == Modo.HTTP401 || actual == Modo.HTTP429) {
            error(intercambio, actual == Modo.HTTP401 ? 401 : 429, "rechazo", "Servicio de prueba");
            return;
        }
        BigDecimal arranque = actor.equals(BUMERAN) ? new BigDecimal("0.0005") : PRECIO_ARRANQUE;
        BigDecimal precio = actor.equals(LINKEDIN) ? new BigDecimal("0.0004")
                : actor.equals(BUMERAN) ? new BigDecimal("0.001") : PRECIO_AVISO;
        JsonNode muestra = muestras.getOrDefault(actor, avisos);
        int porGasto = gastoMaximo.compareTo(arranque) < 0 ? 0
                : gastoMaximo.subtract(arranque).divide(precio, 0, RoundingMode.DOWN).intValue();
        int entregados = Math.min(Math.min(muestra.size(), maxAvisos), porGasto);
        if (actual == Modo.FALLA || actual == Modo.VACIA) { entregados = 0; }
        int numero = secuencia.incrementAndGet();
        Corrida corrida = new Corrida("corrida" + numero, "avisos" + numero, entregados, gastoMaximo, maxAvisos,
                new AtomicInteger(), actor, actual, arranque, precio, muestra);
        corridas.put(corrida.id, corrida);
        escribir(intercambio, 201, sobre(corrida,
                actual == Modo.TARDA || actual == Modo.NO_TERMINA ? "RUNNING" : terminada(corrida)));
    }

    private static boolean entradaValida(String actor, JsonNode entrada, int maxAvisos) {
        if (entrada == null) { return false; }
        if (actor.equals(ACTOR)) {
            return entrada.path("searchQueries").isArray() && !entrada.path("searchQueries").isEmpty()
                    && "pe".equals(entrada.path("country").asString(""));
        }
        if (actor.equals(LINKEDIN)) {
            boolean remoto = "Latin America".equals(entrada.path("location").asString(""));
            return entrada.size() == (remoto ? 5 : 4) && !entrada.path("keywords").asString("").isBlank()
                    && "r604800".equals(entrada.path("datePosted").asString(""))
                    && entrada.path("limit").asInt() == maxAvisos
                    && (remoto ? entrada.path("urlParam").size() == 1
                        && "f_WT".equals(entrada.path("urlParam").path(0).path("key").asString(""))
                        && "2".equals(entrada.path("urlParam").path(0).path("value").asString(""))
                        : "Peru".equals(entrada.path("location").asString("")));
        }
        return entrada.size() == 6 && !entrada.path("query").asString("").isBlank()
                && "PE".equals(entrada.path("country").asString(""))
                && "RECIENTES".equals(entrada.path("sort").asString(""))
                && entrada.path("maxResults").asInt() == maxAvisos
                && entrada.path("includeDetails").asBoolean(false)
                && "text".equals(entrada.path("descriptionFormat").asString(""));
    }

    private void consultar(HttpExchange intercambio, String id) throws IOException {
        Corrida corrida = corridas.get(id);
        if (corrida == null) {
            error(intercambio, 404, "record-not-found", "Actor run was not found");
            return;
        }
        corrida.consultas.incrementAndGet();
        escribir(intercambio, 200, sobre(corrida, terminada(corrida)));
    }

    private void servirAvisos(HttpExchange intercambio, String dataset) throws IOException {
        Corrida corrida = corridas.values().stream().filter(c -> c.dataset.equals(dataset)).findFirst().orElse(null);
        if (corrida == null) {
            error(intercambio, 404, "record-not-found", "Dataset was not found");
            return;
        }
        ArrayNode lista = json.createArrayNode();
        for (int i = 0; i < corrida.entregados; i++) {
            lista.add(corrida.muestra.get(i));
        }
        if (corrida.modo == Modo.JSON_INVALIDO) {
            AccessDePrueba.escribir(intercambio, 200, "application/json", "invalido".getBytes(StandardCharsets.UTF_8));
            return;
        }
        escribir(intercambio, 200, lista);
    }

    private String terminada(Corrida corrida) {
        return corrida.modo == Modo.NO_TERMINA ? "RUNNING" : corrida.modo == Modo.FALLA ? "FAILED" : "SUCCEEDED";
    }

    /** El objeto de la corrida con los campos reales de Apify; el costo, asentado o el de recién terminada. */
    private ObjectNode sobre(Corrida corrida, String estado) {
        boolean asentada = consultasParaAsentar >= 0 && corrida.consultas.get() >= consultasParaAsentar
                && !"RUNNING".equals(estado);
        int cobrados = asentada ? corrida.entregados : 0;
        ObjectNode datos = json.createObjectNode();
        datos.put("id", corrida.id);
        datos.put("actId", corrida.actor);
        datos.put("status", estado);
        datos.put("defaultDatasetId", corrida.dataset);
        if (corrida.modo != Modo.SIN_COSTO) {
            datos.put("usageTotalUsd", corrida.arranque.add(corrida.precio.multiply(BigDecimal.valueOf(cobrados))));
        }
        ObjectNode eventos = datos.putObject("chargedEventCounts");
        eventos.put(EVENTO_ARRANQUE, 1);
        eventos.put(EVENTO_AVISO, cobrados);
        ObjectNode opciones = datos.putObject("options");
        opciones.put("maxItems", corrida.maxAvisos);
        opciones.put("maxTotalChargeUsd", corrida.gastoMaximo.doubleValue());
        if (corrida.modo != Modo.SIN_PRECIO && corrida.modo != Modo.SIN_COSTO) {
            ObjectNode tarifa = (ObjectNode) precios.deepCopy();
            var cargos = (ObjectNode) tarifa.path("pricingPerEvent").path("actorChargeEvents");
            ((ObjectNode) cargos.path(EVENTO_ARRANQUE)).put("eventPriceUsd", corrida.arranque);
            ((ObjectNode) cargos.path(EVENTO_AVISO)).put("eventPriceUsd", corrida.precio);
            datos.set("pricingInfo", tarifa);
        }
        if (corrida.modo == Modo.SIN_COSTO) { datos.remove("chargedEventCounts"); }
        ObjectNode sobre = json.createObjectNode();
        sobre.set("data", datos);
        return sobre;
    }

    private void error(HttpExchange intercambio, int estado, String tipo, String mensaje) throws IOException {
        ObjectNode cuerpo = json.createObjectNode();
        ObjectNode error = cuerpo.putObject("error");
        error.put("type", tipo);
        error.put("message", mensaje);
        escribir(intercambio, estado, cuerpo);
    }

    private void escribir(HttpExchange intercambio, int estado, JsonNode cuerpo) {
        AccessDePrueba.escribir(intercambio, estado, "application/json",
                json.writeValueAsString(cuerpo).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> parametros(String consulta) {
        Map<String, String> parametros = new HashMap<>();
        if (consulta == null) {
            return parametros;
        }
        for (String par : consulta.split("&")) {
            String[] partes = par.split("=", 2);
            parametros.put(URLDecoder.decode(partes[0], StandardCharsets.UTF_8),
                    partes.length > 1 ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "");
        }
        return parametros;
    }

    private record Corrida(String id, String dataset, int entregados, BigDecimal gastoMaximo, int maxAvisos,
            AtomicInteger consultas, String actor, Modo modo, BigDecimal arranque, BigDecimal precio, JsonNode muestra) { }
}
