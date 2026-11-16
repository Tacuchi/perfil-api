package net.tacuchi.perfil.fuentes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.clasificacion.Clasificador;
import net.tacuchi.perfil.clasificacion.Texto;
import net.tacuchi.perfil.clasificacion.Ubicaciones;
import net.tacuchi.perfil.config.PerfilProperties;
import net.tacuchi.perfil.model.Conexion;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Modalidad;
import net.tacuchi.perfil.model.OfertaRecogida;
import net.tacuchi.perfil.repository.ConexionRepository;
import net.tacuchi.perfil.repository.ConsumoRepository;
import net.tacuchi.perfil.service.CifradoClaves;
import net.tacuchi.perfil.service.ConexionesPago;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Computrabajo Perú, por un actor de Apify que busca los términos configurados por recientes. Cada corrida pide un
 * gasto máximo igual a lo que queda del tope del mes y un máximo de avisos, y su costo se suma al consumo del mes de
 * Lima tal como Apify lo informa. Ese costo tarda unos segundos en asentarse tras terminar la corrida: se espera a
 * que los avisos cobrados coincidan con los entregados y, si no se asienta a tiempo, se calcula por el conteo con
 * los precios que informa la propia corrida. La lectura llega cortada por su máximo: nunca es completa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComputrabajoFuente implements FuenteOfertas {

    static final String PAIS = "pe";
    private static final Set<String> TERMINADAS = Set.of("SUCCEEDED", "FAILED", "TIMED-OUT", "ABORTED");
    private static final String EVENTO_AVISO = "apify-default-dataset-item";

    private final ClienteApify apify;
    private final PerfilProperties propiedades;
    private final ConexionRepository conexionRepository;
    private final ConsumoRepository consumoRepository;
    private final ConexionesPago conexionesPago;
    private final CifradoClaves cifrado;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Override
    public Fuente fuente() {
        return Fuente.COMPUTRABAJO;
    }

    @Override
    public Lectura leer() {
        Instant inicio = Instant.now();
        BigDecimal saldo = saldo();
        String clave = clave();
        PerfilProperties.Computrabajo config = propiedades.computrabajo();
        JsonNode corrida = apify.lanzar(config.url(), config.actor(), clave, entrada(config), saldo,
                config.maxAvisos(), config.plazoCorrida(), ClienteApify.ESPERA_MAXIMA_SEGUNDOS);
        String id = corrida.path("id").asString("");
        BigDecimal costo = null;
        int entregados = 0;
        try {
            corrida = esperarFin(clave, id, corrida, inicio);
            String estado = corrida.path("status").asString("");
            if (!"SUCCEEDED".equals(estado)) {
                throw new LecturaFallidaException(EstadoLectura.FALLO,
                        "la corrida del servicio terminó en estado " + estado);
            }
            JsonNode avisos = apify.avisos(config.url(), clave, corrida.path("defaultDatasetId").asString(""));
            entregados = avisos.size();
            costo = costoAsentado(clave, id, corrida, entregados, saldo);
            return new Lectura(convertir(avisos, config.maxAvisos()), false);
        } finally {
            BigDecimal cobrado = costo != null ? costo : porConteo(corrida, entregados, saldo).max(usado(corrida));
            consumoRepository.sumar(Fuente.COMPUTRABAJO, ConsumoRepository.mesDe(clock.instant()), cobrado);
            log.info("[OFERTAS] Computrabajo: corrida de {} avisos con un costo de {} USD", entregados,
                    cobrado.toPlainString());
        }
    }

    /** Lo que queda del tope del mes. Las recolecciones ya omiten una sin saldo; esto lo vuelve a comprobar. */
    private BigDecimal saldo() {
        Conexion conexion = conexionRepository.buscar(Fuente.COMPUTRABAJO);
        conexion.queFalta(cifrado.disponible()).ifPresent(falta -> {
            throw new LecturaFallidaException(EstadoLectura.FALLO, falta);
        });
        BigDecimal saldo = conexion.saldo(conexionesPago.consumoDelMes(Fuente.COMPUTRABAJO));
        if (saldo.signum() <= 0) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "llegó a su tope del mes");
        }
        return saldo;
    }

    private String clave() {
        byte[] guardada = conexionRepository.claveCifrada(Fuente.COMPUTRABAJO)
                .orElseThrow(() -> new LecturaFallidaException(EstadoLectura.FALLO, Conexion.FALTA_CLAVE));
        try {
            return cifrado.descifrar(Fuente.COMPUTRABAJO, guardada);
        } catch (CifradoClaves.ClaveIlegibleException e) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, e.getMessage());
        }
    }

    /** La entrada del actor: los términos en Perú, por recientes, con el detalle de cada aviso. */
    private JsonNode entrada(PerfilProperties.Computrabajo config) {
        ObjectNode entrada = jsonMapper.createObjectNode();
        entrada.put("country", PAIS);
        config.terminos().forEach(entrada.putArray("searchQueries")::add);
        entrada.put("sortBy", "newest");
        entrada.put("maxResults", config.maxAvisos());
        entrada.put("maxResultsPerQuery", (config.maxAvisos() + config.terminos().size() - 1) / config.terminos().size());
        entrada.put("includeJobDetails", true);
        entrada.put("maxConcurrency", 3);
        return entrada;
    }

    private JsonNode esperarFin(String clave, String id, JsonNode corrida, Instant inicio) {
        Instant limite = inicio.plus(propiedades.computrabajo().plazoCorrida());
        JsonNode actual = corrida;
        while (!TERMINADAS.contains(actual.path("status").asString(""))) {
            long restantes = Duration.between(Instant.now(), limite).toSeconds();
            if (restantes <= 0) {
                throw new LecturaFallidaException(EstadoLectura.FALLO, "la corrida del servicio no terminó a tiempo");
            }
            actual = apify.corrida(propiedades.computrabajo().url(), clave, id,
                    (int) Math.min(restantes, ClienteApify.ESPERA_MAXIMA_SEGUNDOS));
        }
        return actual;
    }

    /**
     * Lo que Apify cobró por la corrida, cuando ya cuenta todos los avisos entregados. Recién terminada informa solo
     * el arranque: se vuelve a consultar hasta el plazo y, si no se asienta, se calcula por el conteo.
     */
    private BigDecimal costoAsentado(String clave, String id, JsonNode corrida, int entregados, BigDecimal saldo) {
        PerfilProperties.Computrabajo config = propiedades.computrabajo();
        Instant limite = Instant.now().plus(config.plazoCosto());
        JsonNode actual = corrida;
        while (cobrados(actual) < entregados && Instant.now().isBefore(limite)) {
            dormir(config.pausaConsulta());
            actual = apify.corrida(config.url(), clave, id, 0);
        }
        if (cobrados(actual) >= entregados) {
            return usado(actual);
        }
        log.warn("[OFERTAS] Computrabajo: el costo de la corrida no se asentó a tiempo; se calcula por el conteo");
        return porConteo(actual, entregados, saldo).max(usado(actual));
    }

    private static int cobrados(JsonNode corrida) {
        return corrida.path("chargedEventCounts").path(eventoPrincipal(corrida)).asInt(0);
    }

    private static BigDecimal usado(JsonNode corrida) {
        JsonNode usado = corrida.path("usageTotalUsd");
        return usado.isNumber() ? usado.decimalValue() : BigDecimal.ZERO;
    }

    /**
     * El costo según los precios que informa la corrida: cada aviso entregado más los demás eventos cobrados (al
     * menos uno de cada evento de una sola vez, como el arranque). Sin precios no se puede calcular: se cuenta todo
     * el gasto que se autorizó, para no quedarse corto.
     */
    private static BigDecimal porConteo(JsonNode corrida, int entregados, BigDecimal autorizado) {
        JsonNode eventos = corrida.path("pricingInfo").path("pricingPerEvent").path("actorChargeEvents");
        if (!eventos.isObject() || eventos.isEmpty()) {
            return autorizado;
        }
        String principal = eventoPrincipal(corrida);
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, JsonNode> evento : eventos.properties()) {
            int cobradosEvento = corrida.path("chargedEventCounts").path(evento.getKey()).asInt(0);
            int cantidad = evento.getKey().equals(principal) ? Math.max(cobradosEvento, entregados)
                    : Math.max(cobradosEvento, evento.getValue().path("isOneTimeEvent").asBoolean(false) ? 1 : 0);
            total = total.add(evento.getValue().path("eventPriceUsd").decimalValue()
                    .multiply(BigDecimal.valueOf(cantidad)));
        }
        return total;
    }

    /** El evento que se cobra por aviso: el que el precio marca como principal. */
    private static String eventoPrincipal(JsonNode corrida) {
        JsonNode eventos = corrida.path("pricingInfo").path("pricingPerEvent").path("actorChargeEvents");
        for (Map.Entry<String, JsonNode> evento : eventos.properties()) {
            if (evento.getValue().path("isPrimaryEvent").asBoolean(false)) {
                return evento.getKey();
            }
        }
        return EVENTO_AVISO;
    }

    /** En el orden de llegada, que es el de recientes; sin repetidos y con el máximo configurado. */
    static List<OfertaRecogida> convertir(JsonNode avisos, int maximo) {
        List<OfertaRecogida> ofertas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (JsonNode aviso : avisos) {
            String id = aviso.path("id").asString("");
            String url = url(aviso);
            if (ofertas.size() == maximo) {
                break;
            }
            if (!id.isBlank() && !aviso.path("title").asString("").isBlank() && url != null && vistos.add(id)) {
                ofertas.add(convertir(aviso, id, url));
            }
        }
        return List.copyOf(ofertas);
    }

    private static OfertaRecogida convertir(JsonNode aviso, String id, String url) {
        String texto = texto(aviso);
        String ubicacion = vacioANull(aviso.path("location").asString(""));
        Modalidad modalidad = modalidad(aviso.path("work_arrangement").asString(""));
        String correo = vacioANull(aviso.path("contact_email").asString(""));
        return new OfertaRecogida(
                Fuente.COMPUTRABAJO,
                id,
                aviso.path("title").asString().strip(),
                vacioANull(aviso.path("company").asString("")),
                // La fecha del aviso no ordena: se toma cuando se vio por primera vez.
                null,
                ubicacion,
                modalidad,
                admite(modalidad, aviso.path("country_code").asString(""), ubicacion),
                null,
                Clasificador.detectarIdioma(texto),
                texto,
                url,
                correo != null ? correo.toLowerCase(Locale.ROOT)
                        : ExtractorCorreo.extraer(aviso.path("description_html").asString("")).orElse(null));
    }

    private static String texto(JsonNode aviso) {
        String completo = aviso.path("description_full").asString("");
        return completo.isBlank() ? Texto.deHtml(aviso.path("description_html").asString("")) : completo.strip();
    }

    /** El aviso en Computrabajo; si no lo trae, la página para postular. */
    private static String url(JsonNode aviso) {
        for (String campo : List.of("source_url", "apply_url")) {
            String url = aviso.path(campo).asString("");
            if (url.startsWith("https://")) {
                return url;
            }
        }
        return null;
    }

    private static Modalidad modalidad(String valor) {
        return switch (valor) {
            case "Remote" -> Modalidad.REMOTO;
            case "Hybrid" -> Modalidad.HIBRIDO;
            case "On-site" -> Modalidad.PRESENCIAL;
            default -> Modalidad.NO_INFORMA;
        };
    }

    /** Todo aviso es de Perú: remoto admite a la persona; híbrido o presencial, si es en Lima. */
    private static Boolean admite(Modalidad modalidad, String pais, String ubicacion) {
        if (!"PE".equalsIgnoreCase(pais)) {
            return null;
        }
        return switch (modalidad) {
            case REMOTO -> Boolean.TRUE;
            case HIBRIDO, PRESENCIAL -> Ubicaciones.enLima(ubicacion);
            case NO_INFORMA -> null;
        };
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static void dormir(Duration pausa) {
        try {
            Thread.sleep(pausa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LecturaFallidaException(EstadoLectura.FALLO, "la lectura se interrumpió");
        }
    }
}
