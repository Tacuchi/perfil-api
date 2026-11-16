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
    public static final String ACTOR = "santamaria-automations~computrabajo-scraper";
    public static final BigDecimal PRECIO_ARRANQUE = new BigDecimal("0.001");
    public static final BigDecimal PRECIO_AVISO = new BigDecimal("0.003");
    private static final String EVENTO_ARRANQUE = "apify-actor-start";
    private static final String EVENTO_AVISO = "apify-default-dataset-item";

    /** TERMINA: la corrida acaba dentro de la espera del lanzamiento; TARDA: acaba en la primera consulta. */
    public enum Modo { TERMINA, TARDA, FALLA }

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
    private final AtomicInteger secuencia = new AtomicInteger();
    private volatile Modo modo;
    private volatile int consultasParaAsentar;

    public ApifyDePrueba() {
        avisos = json.readTree(FuentesDePrueba.guardada("apify-computrabajo.json"));
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
        corridas.clear();
        modo = Modo.TERMINA;
        consultasParaAsentar = 1;
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
        if ("POST".equals(intercambio.getRequestMethod()) && ruta.equals("/v2/acts/" + ACTOR + "/runs")) {
            lanzar(intercambio, parametros, cuerpo);
        } else if ("GET".equals(intercambio.getRequestMethod()) && ruta.startsWith("/v2/actor-runs/")) {
            consultar(intercambio, ruta.substring("/v2/actor-runs/".length()));
        } else if ("GET".equals(intercambio.getRequestMethod()) && ruta.startsWith("/v2/datasets/")
                && ruta.endsWith("/items")) {
            servirAvisos(intercambio, ruta.substring("/v2/datasets/".length(), ruta.length() - "/items".length()));
        } else {
            error(intercambio, 404, "page-not-found", "We have bad news: there is nothing here");
        }
    }

    private void lanzar(HttpExchange intercambio, Map<String, String> parametros, JsonNode entrada) throws IOException {
        BigDecimal gastoMaximo;
        int maxAvisos;
        try {
            gastoMaximo = new BigDecimal(parametros.get("maxTotalChargeUsd"));
            maxAvisos = Integer.parseInt(parametros.get("maxItems"));
        } catch (RuntimeException e) {
            error(intercambio, 400, "invalid-input", "maxTotalChargeUsd y maxItems son obligatorios en esta prueba");
            return;
        }
        if (entrada == null || !entrada.path("searchQueries").isArray() || entrada.path("searchQueries").isEmpty()
                || !"pe".equals(entrada.path("country").asString(""))) {
            error(intercambio, 400, "invalid-input", "Input is not valid");
            return;
        }
        int porGasto = gastoMaximo.compareTo(PRECIO_ARRANQUE) < 0 ? 0
                : gastoMaximo.subtract(PRECIO_ARRANQUE).divide(PRECIO_AVISO, 0, RoundingMode.DOWN).intValue();
        int entregados = Math.min(Math.min(avisos.size(), maxAvisos),
                Math.min(entrada.path("maxResults").asInt(avisos.size()), porGasto));
        if (modo == Modo.FALLA) {
            entregados = 0;
        }
        int numero = secuencia.incrementAndGet();
        Corrida corrida = new Corrida("corrida" + numero, "avisos" + numero, entregados, gastoMaximo, maxAvisos);
        corridas.put(corrida.id, corrida);
        escribir(intercambio, 201, sobre(corrida, modo == Modo.TARDA ? "RUNNING" : terminada()));
    }

    private void consultar(HttpExchange intercambio, String id) throws IOException {
        Corrida corrida = corridas.get(id);
        if (corrida == null) {
            error(intercambio, 404, "record-not-found", "Actor run was not found");
            return;
        }
        corrida.consultas.incrementAndGet();
        escribir(intercambio, 200, sobre(corrida, terminada()));
    }

    private void servirAvisos(HttpExchange intercambio, String dataset) throws IOException {
        Corrida corrida = corridas.values().stream().filter(c -> c.dataset.equals(dataset)).findFirst().orElse(null);
        if (corrida == null) {
            error(intercambio, 404, "record-not-found", "Dataset was not found");
            return;
        }
        ArrayNode lista = json.createArrayNode();
        for (int i = 0; i < corrida.entregados; i++) {
            lista.add(avisos.get(i));
        }
        escribir(intercambio, 200, lista);
    }

    private String terminada() {
        return modo == Modo.FALLA ? "FAILED" : "SUCCEEDED";
    }

    /** El objeto de la corrida con los campos reales de Apify; el costo, asentado o el de recién terminada. */
    private ObjectNode sobre(Corrida corrida, String estado) {
        boolean asentada = consultasParaAsentar >= 0 && corrida.consultas.get() >= consultasParaAsentar
                && !"RUNNING".equals(estado);
        int cobrados = asentada ? corrida.entregados : 0;
        ObjectNode datos = json.createObjectNode();
        datos.put("id", corrida.id);
        datos.put("actId", ACTOR);
        datos.put("status", estado);
        datos.put("defaultDatasetId", corrida.dataset);
        datos.put("usageTotalUsd", costo(cobrados).doubleValue());
        ObjectNode eventos = datos.putObject("chargedEventCounts");
        eventos.put(EVENTO_ARRANQUE, 1);
        eventos.put(EVENTO_AVISO, cobrados);
        ObjectNode opciones = datos.putObject("options");
        opciones.put("maxItems", corrida.maxAvisos);
        opciones.put("maxTotalChargeUsd", corrida.gastoMaximo.doubleValue());
        datos.set("pricingInfo", precios);
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
            AtomicInteger consultas) {

        Corrida(String id, String dataset, int entregados, BigDecimal gastoMaximo, int maxAvisos) {
            this(id, dataset, entregados, gastoMaximo, maxAvisos, new AtomicInteger());
        }
    }
}
